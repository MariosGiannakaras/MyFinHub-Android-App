package app.myfinhub.android.feature.bankalert

import android.content.Context
import android.util.AtomicFile
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.EncryptedPayload
import app.myfinhub.android.core.security.SecureValueCipher
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingBankSms(
    val id: String,
    val kind: String,
    val cents: Long,
    val date: String,
    val time: String,
    val merchant: String,
    val lastFour: String,
    val country: String,
) {
    // Random local identifier, not a hash of SMS data. Reuse the normal event ID format
    // on explicit Save, including retry/offline replay, so reopening cannot duplicate it.
    val eventId: String get() = "evt-android-$id"
    fun alert(): BankAlertParser.Alert = BankAlertParser.Alert(
        BankAlertParser.Kind.valueOf(kind), cents, LocalDate.parse(date),
        LocalTime.parse(time), lastFour, merchant, country, "",
    )
    fun valid(): Boolean = runCatching {
        UUID.fromString(id).toString() == id && cents in 1..99_999_999 &&
            LocalDate.parse(date).year in 2000..2100 && LocalTime.parse(time).second == 0 &&
            validMerchantAliasName(merchant) && country.length <= 32 &&
            country.all { it.isLetter() || it == ' ' || it == '-' } &&
            when (BankAlertParser.Kind.valueOf(kind)) {
                BankAlertParser.Kind.CREDIT_PURCHASE -> lastFour.matches(Regex("[0-9]{4}"))
                BankAlertParser.Kind.PURCHASE -> lastFour.isEmpty()
                else -> false
            }
    }.getOrDefault(false)
}

@Serializable
private data class BankSmsEnvelope(
    val slot: String,
    val pending: List<PendingBankSms> = emptyList(),
    val seen: List<String> = emptyList(),
)

/** Owner-bound future SMS only. No raw SMS, account nickname, balance or inbox history on disk. */
internal class BankSmsInboxStore(
    context: Context,
    private val directory: File = File(context.applicationContext.noBackupFilesDir, "bank-sms-inbox"),
    private val cipher: SecureValueCipher = AndroidKeystoreCipher("myfinhub_bank_sms_inbox_v1"),
) {
    private val preferences = context.applicationContext.getSharedPreferences("bank_sms_capture_consent", Context.MODE_PRIVATE)

    fun enabled(owner: String): Boolean = synchronized(lock) { activeSlot() == slot(owner) }
    fun enable(owner: String): Boolean = synchronized(lock) {
        val key = slot(owner)
        read(key) // Do not enable over a corrupted/unreadable queue.
        preferences.edit().putString("owner", key).commit().also { changed() }
    }
    fun disable() = synchronized(lock) {
        if (activeSlot() != null) {
            check(preferences.edit().remove("owner").commit())
            changed()
        }
    }
    fun reconcileOwner(owner: String?) = synchronized(lock) {
        if (activeSlot() != null && activeSlot() != owner?.let(::slot)) disable()
    }
    fun list(owner: String): List<PendingBankSms> = synchronized(lock) { read(slot(owner)).pending }
    fun discard(owner: String, id: String) = synchronized(lock) {
        val key = slot(owner)
        val current = read(key)
        val remaining = current.pending.filterNot { it.id == id }
        if (remaining != current.pending) { write(current.copy(pending = remaining)); changed() }
    }
    fun reconcileConfirmed(owner: String, confirmedIds: Set<String>) = synchronized(lock) {
        if (confirmedIds.isEmpty()) return@synchronized
        val key = slot(owner)
        val current = read(key)
        val remaining = current.pending.filterNot { it.eventId in confirmedIds }
        if (remaining != current.pending) { write(current.copy(pending = remaining)); changed() }
    }

    /** Called only by the permission-gated system receiver. Unknown senders and unsafe bodies are ignored. */
    fun receive(sender: String?, timestamp: Long, body: String): Boolean = synchronized(lock) {
        val key = activeSlot() ?: return@synchronized false
        if (!sender.orEmpty().trim().equals("Piraeus", ignoreCase = true) || timestamp <= 0) return@synchronized false
        val alert = BankAlertParser.parseIncomingPiraeus(body) ?: return@synchronized false
        val pending = PendingBankSms(
            UUID.randomUUID().toString(), alert.kind.name, alert.cents, alert.date.toString(),
            alert.time?.toString() ?: return@synchronized false,
            alert.merchant, alert.lastFour, alert.country,
        )
        if (!pending.valid()) return@synchronized false
        val fingerprint = hash(listOf(timestamp, pending.kind, pending.cents, pending.date, pending.time,
            merchantAliasKey(pending.merchant), pending.lastFour, pending.country).joinToString("\u0000"))
        val current = read(key)
        if (fingerprint in current.seen || current.pending.size >= MAX_PENDING) return@synchronized false
        write(current.copy(
            pending = listOf(pending) + current.pending,
            seen = (current.seen + fingerprint).takeLast(MAX_SEEN),
        ))
        changed()
        true
    }

    private fun activeSlot(): String? = preferences.getString("owner", null)?.takeIf { it.matches(HEX) }
    private fun slot(owner: String): String { require(owner.isNotBlank()); return hash(owner) }
    private fun file(key: String) = AtomicFile(File(directory, "$key.bin"))
    private fun read(key: String): BankSmsEnvelope {
        require(key.matches(HEX))
        val atomic = file(key)
        val raw = try {
            atomic.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count == -1) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(chunk, 0, count)
                }
                output.toByteArray().also { require(it.size in 33..MAX_BYTES) }
            }
        } catch (missing: FileNotFoundException) {
            if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists() ||
                File(atomic.baseFile.path + ".new").exists()) throw missing
            return BankSmsEnvelope(key)
        }
        val buffer = ByteBuffer.wrap(raw)
        require(buffer.int == 12)
        val iv = ByteArray(12).also { buffer.get(it) }
        val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val plain = cipher.decrypt(EncryptedPayload(iv, encrypted))
        val envelope = try { Json.decodeFromString<BankSmsEnvelope>(plain.toString(Charsets.UTF_8)) }
        finally { plain.fill(0) }
        require(envelope.slot == key && envelope.pending.size <= MAX_PENDING && envelope.seen.size <= MAX_SEEN)
        require(envelope.pending.all { it.valid() } && envelope.pending.map { it.id }.distinct().size == envelope.pending.size)
        require(envelope.seen.all { it.matches(HEX) } && envelope.seen.distinct().size == envelope.seen.size)
        return envelope
    }
    private fun write(envelope: BankSmsEnvelope) {
        require(directory.isDirectory || directory.mkdirs())
        val plain = Json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val payload = try { cipher.encrypt(plain) } finally { plain.fill(0) }
        require(payload.initializationVector.size == 12)
        val bytes = ByteBuffer.allocate(4 + 12 + payload.ciphertext.size)
            .putInt(12).put(payload.initializationVector).put(payload.ciphertext).array()
        require(bytes.size <= MAX_BYTES)
        val atomic = file(envelope.slot)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }

    companion object {
        private val lock = Any()
        private val HEX = Regex("[0-9a-f]{64}")
        internal const val MAX_PENDING = 128
        private const val MAX_SEEN = 1024
        private const val MAX_BYTES = 256 * 1024
        val revision = MutableStateFlow(0L)
        private fun changed() { revision.value += 1 }
        private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
