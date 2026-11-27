package app.myfinhub.android.feature.bankalert

import android.content.Context
import android.util.AtomicFile
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.EncryptedPayload
import app.myfinhub.android.core.security.SecureValueCipher
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class BankMerchantAlias(val bankName: String, val nickname: String)

@Serializable
private data class AliasEnvelope(val ownerSlot: String, val aliases: List<BankMerchantAlias>)

private val longNumber = Regex("(?:[0-9][ -]?){12,}")
internal fun validMerchantAliasName(value: String): Boolean = value.trim().length in 1..90 &&
    value.length <= 90 && value.any(Char::isLetter) && !longNumber.containsMatchIn(value) &&
    value.none { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }

/** Exact case/space-normalized descriptor, not fuzzy matching across similarly named merchants. */
internal fun merchantAliasKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
    .trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)

/** Device-only Piraeus merchant names. No messages, card numbers, balances or canonical finance writes. */
internal class BankMerchantAliasStore(
    private val directory: File,
    private val cipher: SecureValueCipher,
) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, "bank-merchant-aliases"),
        AndroidKeystoreCipher("myfinhub_bank_merchant_aliases_v1"),
    )

    suspend fun list(owner: String): List<BankMerchantAlias> = withContext(Dispatchers.IO) {
        synchronized(lock) { read(owner).sortedBy { merchantAliasKey(it.bankName) } }
    }

    suspend fun put(owner: String, bankName: String, nickname: String) = withContext(Dispatchers.IO) {
        require(validMerchantAliasName(bankName) && validMerchantAliasName(nickname))
        synchronized(lock) {
            val rows = read(owner).filterNot { merchantAliasKey(it.bankName) == merchantAliasKey(bankName) } +
                BankMerchantAlias(bankName.trim(), nickname.trim())
            require(rows.size <= MAX_ROWS)
            write(owner, rows)
        }
    }

    suspend fun replace(owner: String, previous: String?, bankName: String, nickname: String) = withContext(Dispatchers.IO) {
        require(validMerchantAliasName(bankName) && validMerchantAliasName(nickname))
        synchronized(lock) {
            val rows = read(owner)
            val oldKey = previous?.let(::merchantAliasKey)
            val newKey = merchantAliasKey(bankName)
            require(rows.none { merchantAliasKey(it.bankName) == newKey && newKey != oldKey })
            val updated = rows.filterNot { merchantAliasKey(it.bankName) == oldKey } +
                BankMerchantAlias(bankName.trim(), nickname.trim())
            require(updated.size <= MAX_ROWS)
            write(owner, updated)
        }
    }

    suspend fun remove(owner: String, bankName: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { write(owner, read(owner).filterNot { merchantAliasKey(it.bankName) == merchantAliasKey(bankName) }) }
    }

    private fun slot(owner: String): String {
        require(owner.isNotBlank())
        return MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun file(ownerSlot: String) = AtomicFile(File(directory, "$ownerSlot.bin"))

    private fun read(owner: String): List<BankMerchantAlias> {
        val ownerSlot = slot(owner)
        val file = file(ownerSlot)
        val raw = try { file.openRead().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val count = input.read(chunk)
                if (count == -1) break
                require(out.size() + count <= MAX_BYTES)
                out.write(chunk, 0, count)
            }
            out.toByteArray().also { require(it.size >= 17) }
        } } catch (missing: java.io.FileNotFoundException) {
            // A genuinely new owner has no file. Do not replace unreadable/corrupt data.
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            return emptyList()
        }
        val buffer = ByteBuffer.wrap(raw)
        val ivSize = buffer.int
        require(ivSize == 12 && buffer.remaining() > ivSize + 16)
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val plaintext = cipher.decrypt(EncryptedPayload(iv, payload))
        val envelope = try { Json.decodeFromString<AliasEnvelope>(plaintext.toString(Charsets.UTF_8)) }
        finally { plaintext.fill(0) }
        require(envelope.ownerSlot == ownerSlot && envelope.aliases.size <= MAX_ROWS)
        require(envelope.aliases.all { validMerchantAliasName(it.bankName) && validMerchantAliasName(it.nickname) })
        require(envelope.aliases.map { merchantAliasKey(it.bankName) }.distinct().size == envelope.aliases.size)
        return envelope.aliases
    }

    private fun write(owner: String, rows: List<BankMerchantAlias>) {
        require(directory.exists() || directory.mkdirs())
        val ownerSlot = slot(owner)
        val plaintext = Json.encodeToString(AliasEnvelope(ownerSlot, rows)).toByteArray(Charsets.UTF_8)
        val payload = try { cipher.encrypt(plaintext) } finally { plaintext.fill(0) }
        require(payload.initializationVector.size == 12)
        val bytes = ByteBuffer.allocate(4 + payload.initializationVector.size + payload.ciphertext.size)
            .putInt(payload.initializationVector.size).put(payload.initializationVector).put(payload.ciphertext).array()
        require(bytes.size <= MAX_BYTES)
        val file = file(ownerSlot)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }

    private companion object {
        val lock = Any()
        const val MAX_ROWS = 512
        const val MAX_BYTES = 512 * 1024
    }
}
