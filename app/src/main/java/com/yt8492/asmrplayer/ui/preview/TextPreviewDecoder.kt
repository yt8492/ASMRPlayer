package com.yt8492.asmrplayer.ui.preview

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class TextEncoding(val label: String) {
    AUTO("自動"), UTF8("UTF-8"), UTF16_LE("UTF-16 LE"), UTF16_BE("UTF-16 BE"), SHIFT_JIS("Shift_JIS"),
}

internal const val MAX_TEXT_BYTES = 5 * 1024 * 1024

internal fun readPreviewText(input: InputStream, checkActive: () -> Unit = {}): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_TEXT_BYTES) { "txtは5 MiBまでプレビューできます。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal data class DecodedPreviewText(val text: String, val encoding: TextEncoding)

internal fun decodePreviewText(bytes: ByteArray, encoding: TextEncoding): DecodedPreviewText {
    fun hasBom(vararg bom: Int) = bytes.size >= bom.size && bom.indices.all { bytes[it].toInt() and 255 == bom[it] }
    fun decode(charset: Charset, skip: Int = 0) = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, skip, bytes.size - skip)).toString()
    val utf8Bom = hasBom(0xef, 0xbb, 0xbf)
    val leBom = hasBom(0xff, 0xfe)
    val beBom = hasBom(0xfe, 0xff)
    if (encoding == TextEncoding.AUTO) {
        return when {
            utf8Bom -> decodePreviewText(bytes, TextEncoding.UTF8)
            leBom -> decodePreviewText(bytes, TextEncoding.UTF16_LE)
            beBom -> decodePreviewText(bytes, TextEncoding.UTF16_BE)
            else -> try { decodePreviewText(bytes, TextEncoding.UTF8) } catch (_: java.nio.charset.CharacterCodingException) {
                decodePreviewText(bytes, TextEncoding.SHIFT_JIS)
            }
        }
    }
    val text = when (encoding) {
        TextEncoding.UTF8 -> decode(Charsets.UTF_8, if (utf8Bom) 3 else 0)
        TextEncoding.UTF16_LE -> decode(Charsets.UTF_16LE, if (leBom) 2 else 0)
        TextEncoding.UTF16_BE -> decode(Charsets.UTF_16BE, if (beBom) 2 else 0)
        TextEncoding.SHIFT_JIS -> decode(Charset.forName("windows-31j"))
        TextEncoding.AUTO -> error("自動判定は解決済み")
    }
    return DecodedPreviewText(text.replace("\r\n", "\n").replace('\r', '\n'), encoding)
}
