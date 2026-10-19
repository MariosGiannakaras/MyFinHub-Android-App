package app.myfinhub.android.core.network

/** Strict, side-effect-free Web provider image envelope validation.
 * SVG is intentionally not passed into Android's raster decoder.
 */
internal fun certifiedProviderImageBytesValid(
    asset: TrustedProviderAssetReference,
    responseMime: String?,
    bytes: ByteArray,
): Boolean {
    val mime=asset.mimeType
    if (mime !in setOf("image/png","image/jpeg","image/webp") ||
        responseMime?.substringBefore(';')?.trim()?.lowercase() != mime ||
        bytes.isEmpty() || bytes.size > 2_097_152 ||
        (asset.sizeBytes != null && (asset.sizeBytes < 1 || asset.sizeBytes > 2_097_152))
    ) return false
    return when (mime) {
        "image/png" -> bytes.size >= 8 &&
            bytes.copyOfRange(0,8).contentEquals(
                byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))
        "image/jpeg" -> bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte()
        "image/webp" -> bytes.size >= 12 &&
            String(bytes,0,4,Charsets.US_ASCII) == "RIFF" &&
            String(bytes,8,4,Charsets.US_ASCII) == "WEBP"
        else -> false
    }
}
