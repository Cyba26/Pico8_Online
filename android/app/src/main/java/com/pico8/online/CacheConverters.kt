package com.pico8.online

import androidx.room.TypeConverter
import java.nio.ByteBuffer

class CacheConverters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.decode(it, Base64.NO_WRAP) }
    }
}

// Simple Base64 utilities
object Base64 {
    private val encodeMap = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    
    fun encodeToString(input: ByteArray, flags: Int = 0): String {
        var padded = false
        val noPad = flags and NO_WRAP != 0
        val noWrap = flags and NO_WRAP != 0
        val lineLen = if (noWrap) Int.MAX_VALUE else 76
        
        val result = StringBuilder()
        var count = 0
        var buffer = 0
        var shift = -6
        
        for (byte in input) {
            buffer = buffer or ((byte.toInt() and 0xFF) shl -shift)
            shift -= 8
            count++
            
            if (count == 3 || shift < -18) {
                result.append(encodeMap[buffer shr 18 and 0x3F])
                result.append(encodeMap[buffer shr 12 and 0x3F])
                result.append(if (count > 2) encodeMap[buffer shr 6 and 0x3F] else '=')
                result.append(if (count > 1) encodeMap[buffer and 0x3F] else '=')
                
                buffer = 0
                shift = -6
                count = 0
                
                if (!noWrap && result.length >= lineLen) {
                    result.append('\n')
                }
            }
        }
        
        if (count > 0) {
            buffer = buffer or ((0 shl -shift) and 0xFFFFFFFF)
            result.append(encodeMap[buffer shr 18 and 0x3F])
            result.append(if (count > 1) encodeMap[buffer shr 12 and 0x3F] else '=')
            result.append(if (count > 2) encodeMap[buffer shr 6 and 0x3F] else '=')
            result.append('=')
        }
        
        if (noPad) {
            return result.toString().replace("=", "")
        }
        
        return result.toString()
    }
    
    fun decode(input: String, flags: Int = 0): ByteArray {
        val noPad = flags and NO_PADDING != 0
        val whiteSpace = flags and NO_WRAP == 0
        
        val map = IntArray(256) { -1 }
        encodeMap.forEachIndexed { index, char ->
            map[char.code] = index
        }
        
        val result = mutableListOf<Byte>()
        var buffer = 0
        var shift = -8
        var count = 0
        
        for (char in input) {
            if (whiteSpace && char.isWhitespace()) continue
            if (char == '=' && noPad) break
            
            val value = map.getOrNull(char.code) ?: continue
            buffer = buffer or (value shl -shift)
            shift -= 6
            count++
            
            if (count == 4 || shift < -24) {
                result.add((buffer shr 16 and 0xFF).toByte())
                result.add((buffer shr 8 and 0xFF).toByte())
                result.add((buffer and 0xFF).toByte())
                
                buffer = 0
                shift = -8
                count = 0
            }
        }
        
        if (count > 0) {
            buffer = buffer or ((0 shl -shift) and 0xFFFFFF)
            if (count > 2) {
                result.add((buffer shr 16 and 0xFF).toByte())
            }
            if (count > 1) {
                result.add((buffer shr 8 and 0xFF).toByte())
            }
        }
        
        return result.toByteArray()
    }
    
    const val NO_WRAP = 2
    const val NO_PADDING = 1
}
