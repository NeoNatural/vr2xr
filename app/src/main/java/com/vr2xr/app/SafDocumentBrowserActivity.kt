package com.vr2xr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vr2xr.R
import com.vr2xr.databinding.ActivitySafDocumentBrowserBinding
import com.vr2xr.saf.SafDocumentEntry
import com.vr2xr.saf.SafTreePermissionState
import com.vr2xr.saf.resolveSafTreePermissionState
import com.vr2xr.saf.sortSafDocumentEntries
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SafDocumentBrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySafDocumentBrowserBinding
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }
    private val docIdStack = ArrayDeque<String>()
    private val folderNameStack = ArrayDeque<String>()

    private lateinit var treeUri: Uri
    private lateinit var currentDocumentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafDocumentBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawTreeUri = intent.getStringExtra(EXTRA_TREE_URI)
        if (rawTreeUri.isNullOrBlank()) {
            finish()
            return
        }

        treeUri = Uri.parse(rawTreeUri)
        currentDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrElse {
                errors.show(getString(R.string.saf_error_invalid_tree))
                finish()
                return
            }

        binding.safBackButton.setOnClickListener { navigateUpOrFinish() }
        binding.safCloseButton.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this) {
            navigateUpOrFinish()
        }

        if (!hasPersistedReadPermission(treeUri)) {
            renderPermissionMissing()
            return
        }

        loadCurrentDirectory()
    }

    private fun navigateUpOrFinish() {
        if (docIdStack.isEmpty()) {
            finish()
            return
        }
        currentDocumentId = docIdStack.removeLast()
        if (folderNameStack.isNotEmpty()) {
            folderNameStack.removeLast()
        }
        loadCurrentDirectory()
    }

    private fun loadCurrentDirectory() {
        binding.safBackButton.isEnabled = docIdStack.isNotEmpty()
        binding.safEntriesContainer.removeAllViews()
        binding.safStatusText.text = getString(R.string.saf_loading)
        binding.safCurrentFolderText.text = currentFolderLabel()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                queryDirectory(currentDocumentId)
            }
            result
                .onSuccess(::renderEntries)
                .onFailure { error ->
                    binding.safEntriesContainer.removeAllViews()
                    binding.safStatusText.text = getString(
                        R.string.saf_error_query_failed,
                        error.message ?: getString(R.string.error_unknown)
                    )
                }
        }
    }

    private fun queryDirectory(documentId: String): Result<List<SafDocumentEntry>> {
        return runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val entries = mutableListOf<SafDocumentEntry>()
            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mime = cursor.getString(mimeIndex)
                    val modified = cursor.getLong(modifiedIndex)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    entries.add(
                        SafDocumentEntry(
                            documentId = docId,
                            uri = docUri.toString(),
                            name = name,
                            mimeType = mime,
                            modifiedMs = modified,
                            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
            } ?: throw IllegalStateException(getString(R.string.saf_error_no_cursor))
            sortSafDocumentEntries(entries)
        }
    }

    private fun renderEntries(entries: List<SafDocumentEntry>) {
        binding.safEntriesContainer.removeAllViews()
        binding.safStatusText.text = if (entries.isEmpty()) {
            getString(R.string.saf_empty_folder)
        } else {
            getString(R.string.saf_entries_count, entries.size)
        }

        entries.forEach { entry ->
            val label = if (entry.isDirectory) {
                getString(R.string.saf_folder_prefix, entry.name)
            } else {
                getString(R.string.saf_video_prefix, entry.name)
            }
            addEntryButton(label) {
                if (entry.isDirectory) {
                    docIdStack.addLast(currentDocumentId)
                    folderNameStack.addLast(entry.name)
                    currentDocumentId = entry.documentId
                    loadCurrentDirectory()
                } else {
                    confirmVideoSelection(entry)
                }
            }
        }
    }

    private fun addEntryButton(label: String, onClick: (View) -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(onClick)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.launcher_section_spacing) / 2
        }
        binding.safEntriesContainer.addView(button, params)
    }

    private fun confirmVideoSelection(entry: SafDocumentEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setMessage(R.string.saf_play_confirm_message)
            .setNegativeButton(R.string.saf_cancel, null)
            .setPositiveButton(R.string.saf_play_action) { _, _ ->
                openVideo(entry)
            }
            .show()
    }

    private fun openVideo(entry: SafDocumentEntry) {
        val source = SourceDescriptor(
            original = entry.uri,
            normalized = entry.uri,
            type = SourceType.LOCAL_URI,
            displayName = entry.name
        )
        lifecycleScope.launch {
            val probe = trackingManager.probeConnection()
            if (probe.connected) {
                startActivity(
                    Intent(this@SafDocumentBrowserActivity, TrackingSetupActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_SOURCE, source)
                )
            } else {
                Toast.makeText(
                    this@SafDocumentBrowserActivity,
                    R.string.toast_glasses_required,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderPermissionMissing() {
        binding.safBackButton.isEnabled = false
        binding.safEntriesContainer.removeAllViews()
        binding.safCurrentFolderText.text = getString(R.string.saf_browser_title)
        binding.safStatusText.text = getString(R.string.saf_error_permission_missing)
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean {
        val persistedReadUris = contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
        return resolveSafTreePermissionState(
            treeUri = uri.toString(),
            persistedReadTreeUris = persistedReadUris
        ) == SafTreePermissionState.GRANTED
    }

    private fun currentFolderLabel(): String {
        val currentName = folderNameStack.lastOrNull() ?: getString(R.string.saf_root_folder)
        return getString(R.string.saf_current_folder, currentName)
    }

    companion object {
        const val EXTRA_TREE_URI = "extra_tree_uri"
    }
}
