package com.vr2xr.saf

import java.util.Locale

private val VIDEO_EXTENSIONS = setOf(
    "mp4",
    "m4v",
    "mkv",
    "mov",
    "webm",
    "ts",
    "m2ts",
    "avi"
)

data class SafDocumentEntry(
    val documentId: String,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val modifiedMs: Long,
    val isDirectory: Boolean
) {
    val isVideo: Boolean
        get() = isSafVideoDocument(name = name, mimeType = mimeType)
}

enum class SafTreePermissionState {
    GRANTED,
    MISSING
}

enum class SafDocumentSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

fun isSafVideoDocument(name: String, mimeType: String?): Boolean {
    if (mimeType?.startsWith("video/") == true) {
        return true
    }
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension in VIDEO_EXTENSIONS
}

fun sortSafDocumentEntries(
    entries: List<SafDocumentEntry>,
    order: SafDocumentSortOrder
): List<SafDocumentEntry> {
    return entries
        .filter { it.isDirectory || it.isVideo }
        .sortedWith(
            compareBy<SafDocumentEntry> { !it.isDirectory }
                .thenComparator { left, right -> compareSafDocumentModifiedTime(left, right, order) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
}

private fun compareSafDocumentModifiedTime(
    left: SafDocumentEntry,
    right: SafDocumentEntry,
    order: SafDocumentSortOrder
): Int {
    val leftUnknown = left.modifiedMs <= 0L
    val rightUnknown = right.modifiedMs <= 0L
    if (leftUnknown != rightUnknown) {
        return if (leftUnknown) 1 else -1
    }
    if (leftUnknown && rightUnknown) {
        return 0
    }
    return when (order) {
        SafDocumentSortOrder.NEWEST_FIRST -> right.modifiedMs.compareTo(left.modifiedMs)
        SafDocumentSortOrder.OLDEST_FIRST -> left.modifiedMs.compareTo(right.modifiedMs)
    }
}

fun resolveSafTreePermissionState(
    treeUri: String,
    persistedReadTreeUris: Collection<String>
): SafTreePermissionState {
    return if (treeUri in persistedReadTreeUris) {
        SafTreePermissionState.GRANTED
    } else {
        SafTreePermissionState.MISSING
    }
}
