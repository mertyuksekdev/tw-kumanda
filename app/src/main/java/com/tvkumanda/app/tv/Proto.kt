package com.tvkumanda.app.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Minimal protobuf writer — enough for the Android TV Remote v2 messages. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    fun int(field: Int, value: Long) { tag(field, 0); varint(value) }
    fun int(field: Int, value: Int) = int(field, value.toLong())

    fun bytes(field: Int, value: ByteArray) {
        tag(field, 2); varint(value.size.toLong()); out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun msg(field: Int, block: ProtoWriter.() -> Unit) =
        bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Minimal protobuf reader: varints become Long, length-delimited fields ByteArray. */
class ProtoMsg private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int) = fields.containsKey(field)
    fun long(field: Int): Long? = fields[field]?.firstOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun msg(field: Int): ProtoMsg? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(buf: ByteArray): ProtoMsg {
            val map = HashMap<Int, MutableList<Any>>()
            var pos = 0
            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val b = buf[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (pos < buf.size) {
                val key = readVarint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> map.getOrPut(field) { mutableListOf() }.add(readVarint())
                    1 -> pos += 8
                    2 -> {
                        val len = readVarint().toInt()
                        map.getOrPut(field) { mutableListOf() }.add(buf.copyOfRange(pos, pos + len))
                        pos += len
                    }
                    5 -> pos += 4
                    else -> return ProtoMsg(map)
                }
            }
            return ProtoMsg(map)
        }
    }
}

/** Messages on both ports are prefixed with their length as a varint. */
object Framing {
    fun write(out: OutputStream, message: ByteArray) {
        val prefix = ProtoWriter().apply { varint(message.size.toLong()) }.toByteArray()
        synchronized(out) {
            out.write(prefix + message)
            out.flush()
        }
    }

    fun read(input: InputStream): ByteArray {
        var len = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Bağlantı kapandı")
            len = len or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        val data = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(data, read, len - read)
            if (n < 0) throw EOFException("Bağlantı kapandı")
            read += n
        }
        return data
    }
}
