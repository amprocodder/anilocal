package com.anilocal.app.data.settings

import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Reads the settings file that Jetpack DataStore (Preferences) wrote before the app had its own
 * settings store — `files/datastore/settings.preferences_pb` — so an update carries the user's
 * settings over. It's a protobuf `PreferenceMap { map<string, Value> preferences = 1; }` where
 * `Value` is a oneof (bool = 1, float = 2, int32 = 3, int64 = 4, string = 5, string set = 6,
 * double = 7, bytes = 8). A few dozen lines of wire-format reading replace a protobuf library.
 * String sets and bytes are skipped (the app never stored either).
 */
internal object LegacyDataStorePreferences {

    fun read(file: File): Map<String, JsonPrimitive> {
        if (!file.isFile) return emptyMap()
        val bytes = file.readBytes()
        val out = LinkedHashMap<String, JsonPrimitive>()
        val map = Reader(bytes, 0, bytes.size)
        while (map.hasMore()) {
            val (field, wire) = map.tag()
            if (field == 1 && wire == LEN) {
                val entry = map.sub()
                var key: String? = null
                var value: JsonPrimitive? = null
                while (entry.hasMore()) {
                    val (f, w) = entry.tag()
                    when {
                        f == 1 && w == LEN -> key = entry.string()
                        f == 2 && w == LEN -> value = value(entry.sub())
                        else -> entry.skip(w)
                    }
                }
                if (key != null && value != null) out[key] = value
            } else {
                map.skip(wire)
            }
        }
        return out
    }

    private fun value(r: Reader): JsonPrimitive? {
        var result: JsonPrimitive? = null
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            result = when (field) {
                1 -> JsonPrimitive(r.varint() != 0L)
                2 -> JsonPrimitive(Float.fromBits(r.fixed32()))
                3 -> JsonPrimitive(r.varint().toInt())
                4 -> JsonPrimitive(r.varint())
                5 -> JsonPrimitive(r.string())
                7 -> JsonPrimitive(Double.fromBits(r.fixed64()))
                else -> { r.skip(wire); result }
            }
        }
        return result
    }

    private const val VARINT = 0
    private const val I64 = 1
    private const val LEN = 2
    private const val I32 = 5

    private class Reader(private val b: ByteArray, private var pos: Int, private val end: Int) {
        fun hasMore() = pos < end

        fun tag(): Pair<Int, Int> {
            val t = varint().toInt()
            return (t ushr 3) to (t and 7)
        }

        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                check(pos < end) { "truncated varint" }
                val byte = b[pos++].toInt()
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                check(shift < 64) { "malformed varint" }
            }
        }

        fun fixed32(): Int {
            check(end - pos >= 4) { "truncated fixed32" }
            var v = 0
            for (i in 0 until 4) v = v or ((b[pos + i].toInt() and 0xFF) shl (8 * i))
            pos += 4
            return v
        }

        fun fixed64(): Long {
            check(end - pos >= 8) { "truncated fixed64" }
            var v = 0L
            for (i in 0 until 8) v = v or ((b[pos + i].toLong() and 0xFF) shl (8 * i))
            pos += 8
            return v
        }

        fun sub(): Reader {
            val len = varint().toInt()
            check(len in 0..(end - pos)) { "bad length" }
            return Reader(b, pos, pos + len).also { pos += len }
        }

        fun string(): String {
            val len = varint().toInt()
            check(len in 0..(end - pos)) { "bad length" }
            return String(b, pos, len, Charsets.UTF_8).also { pos += len }
        }

        fun skip(wire: Int) {
            when (wire) {
                VARINT -> varint()
                I64 -> pos += 8
                LEN -> sub()
                I32 -> pos += 4
                else -> error("unsupported wire type $wire")
            }
            check(pos <= end) { "truncated field" }
        }
    }
}
