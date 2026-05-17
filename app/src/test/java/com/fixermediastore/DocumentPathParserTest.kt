package com.fixermediastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentPathParserTest {

    @Test
    fun parsesPrimaryDcimPath() {
        assertEquals(
            "DCIM/Camera/",
            DocumentPathParser.relativePathFromDocumentId("primary:DCIM/Camera/IMG_20240101_120000.jpg")
        )
    }

    @Test
    fun normalizesPathWithoutTrailingSlash() {
        assertEquals("Pictures/", DocumentPathParser.normalizeRelativePath("Pictures"))
    }

    @Test
    fun invalidDocumentIdReturnsNull() {
        assertNull(DocumentPathParser.relativePathFromDocumentId("no_slash_file.jpg"))
    }
}
