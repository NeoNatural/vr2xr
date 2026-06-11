package com.vr2xr.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafDocumentBrowserPolicyTest {
    @Test
    fun detectsVideoFromMimeType() {
        assertTrue(isSafVideoDocument("stream.bin", "video/x-matroska"))
    }

    @Test
    fun detectsVideoFromKnownExtensionWhenMimeIsGeneric() {
        assertTrue(isSafVideoDocument("sample.M2TS", "application/octet-stream"))
    }

    @Test
    fun rejectsNonVideoDocuments() {
        assertFalse(isSafVideoDocument("notes.txt", "text/plain"))
    }

    @Test
    fun sortsDirectoriesBeforeVideosByName() {
        val entries = listOf(
            entry(name = "zeta.mp4", isDirectory = false, mimeType = "video/mp4"),
            entry(name = "Beta", isDirectory = true, mimeType = null),
            entry(name = "alpha.mkv", isDirectory = false, mimeType = "application/octet-stream"),
            entry(name = "Alpha", isDirectory = true, mimeType = null),
            entry(name = "ignored.txt", isDirectory = false, mimeType = "text/plain")
        )

        val sortedNames = sortSafDocumentEntries(entries).map { it.name }

        assertEquals(listOf("Alpha", "Beta", "alpha.mkv", "zeta.mp4"), sortedNames)
    }

    @Test
    fun reportsGrantedTreePermissionWhenPersistedReadUriMatches() {
        val state = resolveSafTreePermissionState(
            treeUri = "content://provider/tree/share%3A",
            persistedReadTreeUris = listOf("content://provider/tree/share%3A")
        )

        assertEquals(SafTreePermissionState.GRANTED, state)
    }

    @Test
    fun reportsMissingTreePermissionWhenPersistedReadUriDoesNotMatch() {
        val state = resolveSafTreePermissionState(
            treeUri = "content://provider/tree/share%3A",
            persistedReadTreeUris = listOf("content://provider/tree/other%3A")
        )

        assertEquals(SafTreePermissionState.MISSING, state)
    }

    private fun entry(
        name: String,
        isDirectory: Boolean,
        mimeType: String?
    ): SafDocumentEntry {
        return SafDocumentEntry(
            documentId = name,
            uri = "content://provider/document/$name",
            name = name,
            mimeType = mimeType,
            modifiedMs = 0L,
            isDirectory = isDirectory
        )
    }
}
