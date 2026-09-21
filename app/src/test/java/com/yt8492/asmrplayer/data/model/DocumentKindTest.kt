package com.yt8492.asmrplayer.data.model

import org.junit.Assert.*
import org.junit.Test

class DocumentKindTest {
    @Test fun MIMEを優先し不明なときだけ拡張子で補完する() {
        assertEquals(DocumentKind.PDF, documentKind("application/pdf", "説明.txt"))
        assertEquals(DocumentKind.TEXT, documentKind("text/plain", "説明.pdf"))
        assertEquals(DocumentKind.PDF, documentKind("application/octet-stream", "説明.PDF"))
        assertEquals(DocumentKind.TEXT, documentKind("", "説明.TxT"))
        assertNull(documentKind("image/jpeg", "説明.pdf"))
        assertNull(documentKind("text/html", "説明.txt"))
        assertNull(documentKind("", "説明.zip"))
    }
}
