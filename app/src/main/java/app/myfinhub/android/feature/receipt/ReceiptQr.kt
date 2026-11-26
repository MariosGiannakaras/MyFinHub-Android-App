package app.myfinhub.android.feature.receipt

import java.net.URI
import java.time.LocalDateTime
import java.security.MessageDigest
import java.util.Locale

/**
 * The AADE A.1135/2023 fiscal-register QR format embeds the amount and local
 * timestamp. Decoding is NOT online fiscal verification or proof of authenticity.
 * We never open or send any scanned URL; unknown provider links fall back to OCR.
 */
internal data class FiscalReceiptQr(
    val totalCents: Long,
    val issuedAt: LocalDateTime,
    // Ephemeral identity distinguishes separate receipts in one image.
    val identity: String,
)

internal enum class ReceiptQrStatus { NONE, UNSUPPORTED, AMBIGUOUS, AADE }

internal data class ReceiptQrRead(
    val status: ReceiptQrStatus = ReceiptQrStatus.NONE,
    val fiscal: FiscalReceiptQr? = null,
)

/** Strictly recognize the documented host/path and SIG grammar, not arbitrary URL text. */
internal fun parseAadeReceiptQr(payload: String): FiscalReceiptQr? {
    if (payload.length !in 1..256) return null
    val uri = runCatching { URI(payload) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("www1.aade.gr", ignoreCase = true) ||
        uri.port != -1 || uri.rawUserInfo != null || uri.rawFragment != null ||
        uri.rawPath != "/tameiakes/myweb/q1.php") return null
    val (serial, sequence, signature, timestamp, amount) =
        Regex("""SIG=([A-Za-z0-9]{11})([0-9]{8})([A-Fa-f0-9]{30})([0-9]{10})([0-9]{1,6}(?:\.[0-9]{1,2})?)""")
            .matchEntire(uri.rawQuery.orEmpty())?.destructured ?: return null
    val issued = runCatching {
        LocalDateTime.of(
            2000 + timestamp.substring(0, 2).toInt(),
            timestamp.substring(2, 4).toInt(),
            timestamp.substring(4, 6).toInt(),
            timestamp.substring(6, 8).toInt(),
            timestamp.substring(8, 10).toInt(),
        )
    }.getOrNull() ?: return null
    val cents = receiptReviewedCents(amount) ?: return null
    val identity = MessageDigest.getInstance("SHA-256")
        .digest((serial + sequence + signature).uppercase(Locale.ROOT).toByteArray(Charsets.US_ASCII))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return FiscalReceiptQr(cents, issued, identity)
}

internal fun classifyReceiptQrs(payloads: List<String>): ReceiptQrRead {
    if (payloads.isEmpty()) return ReceiptQrRead()
    if (payloads.size > 16) return ReceiptQrRead(ReceiptQrStatus.AMBIGUOUS)
    val unique = payloads.distinct()
    val parsed = unique.mapNotNull(::parseAadeReceiptQr).distinct()
    // Marketing/provider QR beside exactly one valid fiscal code is common.
    // An unparseable AADE claim, however, is not safe to choose over another code.
    val invalidAade = unique.any {
        parseAadeReceiptQr(it) == null && it.contains("aade.gr/tameiakes/", ignoreCase = true)
    }
    return when {
        parsed.size > 1 || (parsed.isNotEmpty() && invalidAade) -> ReceiptQrRead(ReceiptQrStatus.AMBIGUOUS)
        parsed.size == 1 -> ReceiptQrRead(ReceiptQrStatus.AADE, parsed.single())
        else -> ReceiptQrRead(ReceiptQrStatus.UNSUPPORTED)
    }
}

internal data class ReceiptQrReview(
    val status: ReceiptQrStatus,
    val fiscal: FiscalReceiptQr? = null,
    val ocrTotalCents: Long? = null,
    val ocrDate: java.time.LocalDate? = null,
    val requiresConfirmation: Boolean = false,
    val ocrAmbiguous: Boolean = false,
)

/** Prefer fiscal QR data, while preserving OCR disagreement and the foreign-currency fence. */
internal fun mergeReceiptQr(ocr: ReceiptProposal, read: ReceiptQrRead): ReceiptProposal {
    val fiscal = read.fiscal
    if (read.status != ReceiptQrStatus.AADE || fiscal == null) {
        return ocr.copy(qrReview = ReceiptQrReview(read.status))
    }
    val disagreement = (ocr.totalCents != null && ocr.totalCents != fiscal.totalCents) ||
        (ocr.date != null && ocr.date != fiscal.issuedAt.toLocalDate()) ||
        ocr.currency == ReceiptCurrency.FOREIGN || ocr.ocrAmbiguous
    return ocr.copy(
        totalCents = fiscal.totalCents,
        date = fiscal.issuedAt.toLocalDate(),
        currency = if (ocr.currency == ReceiptCurrency.FOREIGN) ReceiptCurrency.FOREIGN else ReceiptCurrency.EUR,
        qrReview = ReceiptQrReview(read.status, fiscal, ocr.totalCents, ocr.date, disagreement, ocr.ocrAmbiguous),
    )
}
