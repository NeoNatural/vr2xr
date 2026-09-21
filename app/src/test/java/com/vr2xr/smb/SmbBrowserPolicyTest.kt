package com.vr2xr.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbBrowserPolicyTest {
    @Test
    fun detectsVideoFromKnownExtension() {
        assertTrue(isSmbVideo(entry("sample.M2TS")))
    }

    @Test
    fun rejectsNonVideoFilesAndDirectories() {
        assertFalse(isSmbVideo(entry("notes.txt")))
        assertFalse(isSmbVideo(entry("movies", directory = true)))
    }

    @Test
    fun sortsDirectoriesBeforeVideosAndFiltersOtherFiles() {
        val entries = listOf(
            entry("old-video.mp4", modified = 100L),
            entry("new-folder", directory = true, modified = 400L),
            entry("new-video.mkv", modified = 300L),
            entry("old-folder", directory = true, modified = 200L),
            entry("ignored.txt")
        )

        val sorted = sortSmbEntries(entries, SmbSortOrder.NEWEST_FIRST).map { it.name }

        assertEquals(listOf("new-folder", "old-folder", "new-video.mkv", "old-video.mp4"), sorted)
    }

    @Test
    fun supportsOldestFirstAndUnknownTimesLast() {
        val entries = listOf(
            entry("unknown.mp4"),
            entry("new.mkv", modified = 300L),
            entry("old.mp4", modified = 100L)
        )

        val sorted = sortSmbEntries(entries, SmbSortOrder.OLDEST_FIRST).map { it.name }

        assertEquals(listOf("old.mp4", "new.mkv", "unknown.mp4"), sorted)
    }

    @Test
    fun profileIdentityIsStableButAccountSpecific() {
        val first = SmbProfile.create("nas.local", "media", "", "neo", "one")
        val sameIdentity = SmbProfile.create("NAS.LOCAL", "MEDIA", "", "NEO", "two")
        val anotherAccount = SmbProfile.create("nas.local", "media", "", "guest", "one")

        assertEquals(first.id, sameIdentity.id)
        assertNotEquals(first.id, anotherAccount.id)
    }

    private fun entry(name: String, directory: Boolean = false, modified: Long = 0L) = MediaEntry(
        uri = "smb://nas/media/$name${if (directory) "/" else ""}",
        name = name,
        directory = directory,
        size = if (directory) 0L else 100L,
        modifiedTime = modified
    )
}
