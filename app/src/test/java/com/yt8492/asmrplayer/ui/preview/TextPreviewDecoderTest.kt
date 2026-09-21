package com.yt8492.asmrplayer.ui.preview

import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import org.junit.Assert.*
import org.junit.Test

class TextPreviewDecoderTest {
    private val text = "日本語の説明です。\n次の行"

    @Test fun BOM付きUTF8とUTF16およびShiftJISを読み込める() {
        val examples = listOf(
            text.toByteArray(),
            byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray(),
            byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE),
            byteArrayOf(0xfe.toByte(), 0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE),
            text.toByteArray(Charset.forName("windows-31j")),
        )
        val expectedEncodings = listOf(TextEncoding.UTF8, TextEncoding.UTF8, TextEncoding.UTF16_LE,
            TextEncoding.UTF16_BE, TextEncoding.SHIFT_JIS)
        examples.zip(expectedEncodings).forEach { (bytes, expectedEncoding) ->
            val decoded = decodePreviewText(bytes, TextEncoding.AUTO)
            assertEquals(text, decoded.text)
            assertEquals(expectedEncoding, decoded.encoding)
        }
    }

    @Test fun 自動判定と手動の文字コード指定を切り替えられる() {
        val bytes = text.toByteArray(Charset.forName("windows-31j"))
        assertEquals(text, decodePreviewText(bytes, TextEncoding.SHIFT_JIS).text)
        assertThrows(CharacterCodingException::class.java) { decodePreviewText(bytes, TextEncoding.UTF8) }
    }

    @Test fun 空ファイルと改行形式を扱える() {
        assertEquals("", decodePreviewText(byteArrayOf(), TextEncoding.AUTO).text)
        assertEquals("a\nb\nc", decodePreviewText("a\r\nb\rc".toByteArray(), TextEncoding.AUTO).text)
    }

    @Test fun サイズ不明でも実際の読込量で上限を検証する() {
        assertEquals(MAX_TEXT_BYTES, readPreviewText(ByteArray(MAX_TEXT_BYTES).inputStream()).size)
        assertThrows(IllegalArgumentException::class.java) {
            readPreviewText(ByteArray(MAX_TEXT_BYTES + 1).inputStream())
        }
    }

    @Test fun 読み込み途中でキャンセルできる() {
        assertThrows(IllegalStateException::class.java) {
            readPreviewText(ByteArray(10).inputStream()) { error("中止") }
        }
    }
}
