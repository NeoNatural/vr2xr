package com.vr2xr.smb

import java.util.Locale

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mkv", "mov", "webm", "ts", "m2ts", "avi"
)

enum class SmbSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

fun isSmbVideo(entry: MediaEntry): Boolean {
    if (entry.directory) return false
    val extension = entry.name.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension in VIDEO_EXTENSIONS
}

fun sortSmbEntries(entries: List<MediaEntry>, order: SmbSortOrder): List<MediaEntry> =
    entries
        .filter { it.directory || isSmbVideo(it) }
        .sortedWith(
            compareBy<MediaEntry> { !it.directory }
                .thenComparator { left, right -> compareModifiedTime(left, right, order) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

private fun compareModifiedTime(left: MediaEntry, right: MediaEntry, order: SmbSortOrder): Int {
    val leftUnknown = left.modifiedTime <= 0L
    val rightUnknown = right.modifiedTime <= 0L
    if (leftUnknown != rightUnknown) return if (leftUnknown) 1 else -1
    if (leftUnknown) return 0
    return when (order) {
        SmbSortOrder.NEWEST_FIRST -> right.modifiedTime.compareTo(left.modifiedTime)
        SmbSortOrder.OLDEST_FIRST -> left.modifiedTime.compareTo(right.modifiedTime)
    }
}
