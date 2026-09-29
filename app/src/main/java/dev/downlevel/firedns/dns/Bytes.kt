package dev.downlevel.firedns.dns

/** Big-endian (network order) reads/writes on ByteArray. */
internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long = (u16(offset).toLong() shl 16) or u16(offset + 2).toLong()

internal fun ByteArray.put16(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.put32(offset: Int, value: Long) {
    put16(offset, (value shr 16).toInt())
    put16(offset + 2, value.toInt())
}
