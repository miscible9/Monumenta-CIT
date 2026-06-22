package com.monumenta.reborn.cit.load.binary

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class BinaryIndexReader(private val input: InputStream) : Closeable {
    fun expectMagic(expected: ByteArray) {
        val actual = input.readNBytes(expected.size)
        if (!actual.contentEquals(expected)) {
            throw IOException("Invalid binary CIT index magic")
        }
    }

    fun readInt(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun readUnsignedByte(): Int {
        val value = input.read()
        if (value < 0) throw EOFException()
        return value
    }

    fun readBoolean(): Boolean = readUnsignedByte() != 0

    fun readString(): String {
        val length = readInt()
        if (length < 0) throw IOException("Negative string length in binary CIT index")
        val bytes = input.readNBytes(length)
        if (bytes.size != length) throw EOFException()
        return bytes.toString(Charsets.UTF_8)
    }

    override fun close() {
        input.close()
    }
}
