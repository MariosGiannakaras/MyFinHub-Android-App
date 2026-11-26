package app.myfinhub.android.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
private const val MAX_OCR_EDGE = 2000
private const val MAX_ORIGINAL_EDGE = 20000
private val modelNames = listOf("ell.traineddata", "eng.traineddata")
private val modelInstallLock = Any()
private val recognitionMutex = Mutex()

/** No network, persistence of receipt pixels, logs, or raw OCR text. */
internal suspend fun recognizeReceipt(context: Context, uri: Uri): ReceiptProposal = withContext(Dispatchers.IO) {
    recognitionMutex.withLock {
    require(uri.scheme == "content") { "Unsupported image source" }
    val raw = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= MAX_IMAGE_BYTES) { "Image too large" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IOException("Unavailable image")
    var bitmap: Bitmap? = null
    try {
        require(isReceiptImage(raw)) { "Unsupported image" }
        val decoded = decodeReceiptBitmap(raw)
        bitmap = decoded
        ensureActive()
        recognizeReceiptBitmap(context, decoded)
    } finally {
        bitmap?.recycle()
        raw.fill(0)
    }
    }
}

internal fun isReceiptImage(bytes: ByteArray): Boolean =
    (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
        bytes[2] == 0xff.toByte()) ||
        (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
        ))

private fun decodeReceiptBitmap(raw: ByteArray): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(raw))) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width in 1..MAX_ORIGINAL_EDGE && height in 1..MAX_ORIGINAL_EDGE)
            val edge = maxOf(width, height)
            if (edge > MAX_OCR_EDGE) decoder.setTargetSize(
                (width.toLong() * MAX_OCR_EDGE / edge).toInt().coerceAtLeast(1),
                (height.toLong() * MAX_OCR_EDGE / edge).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
    require(options.outWidth in 1..MAX_ORIGINAL_EDGE && options.outHeight in 1..MAX_ORIGINAL_EDGE)
    val sample = generateSequence(1) { it * 2 }
        .first { maxOf(options.outWidth, options.outHeight) / it <= MAX_OCR_EDGE }
    return BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: throw IOException("Invalid image")
}

private fun installModels(context: Context): File = synchronized(modelInstallLock) {
    val root = File(context.noBackupFilesDir, "receipt-ocr-4.1.0")
    val data = File(root, "tessdata").apply { check(isDirectory || mkdirs()) }
    for (name in modelNames) {
        val destination = File(data, name)
        // Verify the immutable shipped bytes on each installation/use. Models contain no user data.
        val expected = when (name) {
            "ell.traineddata" -> 1419514L
            else -> 4113088L
        }
        val hash = if (name == "ell.traineddata") "ed98ae1a88d84414da316e6eeab3232f2c68639b"
            else "bbef4675053b5b468cdb477053e28b1c698ba08e"
        if (!matchesModel(destination, expected, hash)) {
            val temporary = File(data, "$name.tmp")
            try {
                context.assets.open("receipt-tessdata/$name").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(matchesModel(temporary, expected, hash))
                check(temporary.renameTo(destination))
            } finally {
                temporary.delete()
            }
        }
    }
    root
}

private fun matchesModel(file: File, length: Long, hash: String): Boolean {
    if (!file.isFile || file.length() != length) return false
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    digest.update("blob $length\u0000".toByteArray(Charsets.UTF_8))
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == hash
}

/** Each recognition owns its own non-thread-safe native recognizer and releases it. */
internal fun recognizeReceiptBitmap(context: Context, bitmap: Bitmap): ReceiptProposal {
    val root = installModels(context)
    val engine = TessBaseAPI()
    try {
        check(engine.init(root.absolutePath + File.separator, "ell+eng", TessBaseAPI.OEM_LSTM_ONLY))
        engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        engine.setImage(bitmap)
        return parseReceiptText(engine.getUTF8Text().orEmpty())
    } finally {
        engine.recycle()
    }
}
