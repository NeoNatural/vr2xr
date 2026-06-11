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

fun isSafVideoDocument(name: String, mimeType: String?): Boolean {
    if (mimeType?.startsWith("video/") == true) {
        return true
    }
    val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    return extension in VIDEO_EXTENSIONS
}

fun sortSafDocumentEntries(entries: List<SafDocumentEntry>): List<SafDocumentEntry> {
    return entries
        .filter { it.isDirectory || it.isVideo }
        .sortedWith(
            compareBy<SafDocumentEntry> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
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
