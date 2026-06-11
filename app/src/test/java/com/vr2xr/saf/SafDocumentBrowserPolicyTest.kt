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
    fun sortsDirectoriesBeforeVideosByNewestFirstByDefault() {
        val entries = listOf(
            entry(name = "old-video.mp4", isDirectory = false, mimeType = "video/mp4", modifiedMs = 100L),
            entry(name = "new-folder", isDirectory = true, mimeType = null, modifiedMs = 400L),
            entry(name = "new-video.mkv", isDirectory = false, mimeType = "application/octet-stream", modifiedMs = 300L),
            entry(name = "old-folder", isDirectory = true, mimeType = null, modifiedMs = 200L),
            entry(name = "ignored.txt", isDirectory = false, mimeType = "text/plain")
        )

        val sortedNames = sortSafDocumentEntries(entries, SafDocumentSortOrder.NEWEST_FIRST).map { it.name }

        assertEquals(listOf("new-folder", "old-folder", "new-video.mkv", "old-video.mp4"), sortedNames)
    }

    @Test
    fun sortsDirectoriesBeforeVideosByOldestFirst() {
        val entries = listOf(
            entry(name = "old-video.mp4", isDirectory = false, mimeType = "video/mp4", modifiedMs = 100L),
            entry(name = "new-folder", isDirectory = true, mimeType = null, modifiedMs = 400L),
            entry(name = "new-video.mkv", isDirectory = false, mimeType = "application/octet-stream", modifiedMs = 300L),
            entry(name = "old-folder", isDirectory = true, mimeType = null, modifiedMs = 200L)
        )

        val sortedNames = sortSafDocumentEntries(entries, SafDocumentSortOrder.OLDEST_FIRST).map { it.name }

        assertEquals(listOf("old-folder", "new-folder", "old-video.mp4", "new-video.mkv"), sortedNames)
    }

    @Test
    fun fallsBackToNameWhenModifiedTimesMatch() {
        val entries = listOf(
            entry(name = "zeta.mp4", isDirectory = false, mimeType = "video/mp4", modifiedMs = 100L),
            entry(name = "alpha.mkv", isDirectory = false, mimeType = "application/octet-stream", modifiedMs = 100L)
        )

        val sortedNames = sortSafDocumentEntries(entries, SafDocumentSortOrder.NEWEST_FIRST).map { it.name }

        assertEquals(listOf("alpha.mkv", "zeta.mp4"), sortedNames)
    }

    @Test
    fun unknownModifiedTimeSortsAfterKnownTimeWhenNewestFirst() {
        val entries = listOf(
            entry(name = "unknown.mp4", isDirectory = false, mimeType = "video/mp4", modifiedMs = 0L),
            entry(name = "known.mkv", isDirectory = false, mimeType = "application/octet-stream", modifiedMs = 100L)
        )

        val sortedNames = sortSafDocumentEntries(entries, SafDocumentSortOrder.NEWEST_FIRST).map { it.name }

        assertEquals(listOf("known.mkv", "unknown.mp4"), sortedNames)
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
        mimeType: String?,
        modifiedMs: Long = 0L
    ): SafDocumentEntry {
        return SafDocumentEntry(
            documentId = name,
            uri = "content://provider/document/$name",
            name = name,
            mimeType = mimeType,
            modifiedMs = modifiedMs,
            isDirectory = isDirectory
        )
    }
}
