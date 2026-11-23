package com.rfid.rolltrack.scanner

import java.math.BigInteger
import java.util.Locale

object TagIdNormalizer {

    enum class ByteOrder {
        BIG_ENDIAN,
        LITTLE_ENDIAN
    }

    enum class ErpFormat {
        CANONICAL_HEX,
        HEX_COLONS,
        DECIMAL_BE,
        DECIMAL_LE
    }

    private val separators = Regex("[\\s:\\-]")
    private val hexChars = Regex("^[0-9A-F]+$")

    fun fromUidBytes(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun fromRaw(raw: String, decimalByteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val s = separators.replace(trimmed, "").uppercase(Locale.ROOT)
        if (s.isEmpty()) return null

        if (hexChars.matches(s) && isHexUidLength(s.length)) {
            return s
        }

        if (s.all { it.isDigit() }) {
            try {
                val value = BigInteger(s)
                val len = if (value < BigInteger("FFFFFFFF", 16)) 8 else 14
                return decimalToHex(value, len, decimalByteOrder)
            } catch (_: Exception) {
                return null
            }
        }

        return null
    }

    fun toErp(canonical: String, format: ErpFormat): String {
        return when (format) {
            ErpFormat.CANONICAL_HEX -> canonical.uppercase(Locale.ROOT)
            ErpFormat.HEX_COLONS -> canonical.chunked(2).joinToString(":")
            ErpFormat.DECIMAL_BE -> toDecimal(canonical, ByteOrder.BIG_ENDIAN)
            ErpFormat.DECIMAL_LE -> toDecimal(canonical, ByteOrder.LITTLE_ENDIAN)
        }
    }

    fun fromErp(value: String, format: ErpFormat): String {
        return when (format) {
            ErpFormat.CANONICAL_HEX, ErpFormat.HEX_COLONS -> fromRaw(value, ByteOrder.BIG_ENDIAN) ?: value
            ErpFormat.DECIMAL_BE -> fromRaw(value, ByteOrder.BIG_ENDIAN) ?: value
            ErpFormat.DECIMAL_LE -> fromRaw(value, ByteOrder.LITTLE_ENDIAN) ?: value
        }
    }

    fun allFormats(canonical: String): List<Pair<String, String>> {
        return listOf(
            "Canonical (hex, big-endian)" to canonical,
            "Hex with colons (NFC Tools)" to canonical.chunked(2).joinToString(":"),
            "Decimal big-endian" to toDecimal(canonical, ByteOrder.BIG_ENDIAN),
            "Decimal little-endian (USB reader)" to toDecimal(canonical, ByteOrder.LITTLE_ENDIAN)
        )
    }

    fun short(canonical: String): String {
        return if (canonical.length <= 8) canonical else canonical.take(4) + ".." + canonical.takeLast(4)
    }

    /**
     * UHF Gen2 EPC as sent by the Seuic UHF service: 96-bit (24 hex) or 128-bit (32 hex), no separators.
     * Kept separate from [fromRaw] so other workflows' classification is unchanged.
     */
    private val epcPattern = Regex("^(?:[0-9A-F]{24}|[0-9A-F]{32})$")

    fun isEpc(value: String): Boolean = epcPattern.matches(value.trim().uppercase(Locale.ROOT))

    /** One scanner payload can hold several reads (the UHF service joins EPCs with line breaks). */
    fun splitReads(raw: String): List<String> =
        raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    // 8/14/20 = NFC UIDs; 24/32 = UHF EPC-96/EPC-128 roll tags from the PDA reader
    private fun isHexUidLength(len: Int): Boolean = len == 8 || len == 14 || len == 20 || len == 24 || len == 32

    private fun decimalToHex(value: BigInteger, hexLength: Int, order: ByteOrder): String {
        val hex = value.toString(16).uppercase(Locale.ROOT).padStart(hexLength, '0')
        return if (order == ByteOrder.LITTLE_ENDIAN) reverseBytes(hex) else hex
    }

    private fun toDecimal(canonical: String, order: ByteOrder): String {
        val hex = if (order == ByteOrder.LITTLE_ENDIAN) reverseBytes(canonical) else canonical
        return BigInteger(hex, 16).toString()
    }

    private fun reverseBytes(hex: String): String {
        return hex.chunked(2).reversed().joinToString("")
    }
}
