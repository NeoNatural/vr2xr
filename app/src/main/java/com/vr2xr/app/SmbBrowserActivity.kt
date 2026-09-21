package com.vr2xr.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vr2xr.R
import com.vr2xr.databinding.ActivitySmbBrowserBinding
import com.vr2xr.smb.MediaEntry
import com.vr2xr.smb.SmbProfile
import com.vr2xr.smb.SmbSortOrder
import com.vr2xr.smb.SmbThumbnailLoader
import com.vr2xr.smb.sortSmbEntries
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmbBrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySmbBrowserBinding
    private val app by lazy { application as Vr2xrApplication }
    private val manager by lazy { app.smbClientManager }
    private val profileStore by lazy { app.smbProfileStore }
    private val trackingManager by lazy { app.trackingSessionManager }

    private var activeProfile: SmbProfile? = null
    private var currentUri: String? = null
    private val parentUris = ArrayDeque<String>()
    private var currentEntries = emptyList<MediaEntry>()
    private var sortOrder = SmbSortOrder.NEWEST_FIRST
    private var networkJob: Job? = null
    private var requestGeneration = 0L
    private var thumbnailLoader: SmbThumbnailLoader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmbBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.smbConnectButton.setOnClickListener { connectFromForm() }
        binding.smbBackButton.setOnClickListener { navigateUpOrFinish() }
        binding.smbSortButton.setOnClickListener { toggleSortOrder() }
        binding.smbEntriesList.layoutManager = LinearLayoutManager(this)
        onBackPressedDispatcher.addCallback(this) { navigateUpOrFinish() }
        renderSavedProfiles()
    }

    override fun onDestroy() {
        requestGeneration += 1
        networkJob?.cancel()
        thumbnailLoader?.close()
        thumbnailLoader = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (activeProfile != null && thumbnailLoader == null) renderDirectory()
    }

    override fun onStop() {
        thumbnailLoader?.close()
        thumbnailLoader = null
        binding.smbEntriesList.adapter = null
        super.onStop()
    }

    private fun connectFromForm() {
        val profile = runCatching {
            SmbProfile.create(
                host = binding.smbHostInput.text?.toString().orEmpty(),
                share = binding.smbShareInput.text?.toString().orEmpty(),
                domain = binding.smbDomainInput.text?.toString().orEmpty(),
                username = binding.smbUsernameInput.text?.toString().orEmpty(),
                password = binding.smbPasswordInput.text?.toString().orEmpty()
            ).also {
                com.vr2xr.smb.SmbStorage.validateTargetPart(it.host, "host")
                com.vr2xr.smb.SmbStorage.validateTargetPart(it.share, "share")
            }
        }.getOrElse {
            showStatus(it.message ?: getString(R.string.smb_error_invalid_profile))
            return
        }
        connect(profile, binding.smbRememberAccount.isChecked)
    }

    private fun connect(profile: SmbProfile, remember: Boolean) {
        val generation = beginRequest(getString(R.string.smb_connecting))
        networkJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { manager.connect(profile, remember) }
            }
            if (generation != requestGeneration) return@launch
            setBusy(false)
            result.onSuccess { entries ->
                activeProfile = profile
                currentUri = "smb://${profile.host}/${profile.share}/"
                parentUris.clear()
                currentEntries = entries
                renderSavedProfiles()
                renderDirectory()
            }.onFailure { error ->
                showStatus(getString(R.string.smb_error_connect_failed, safeErrorMessage(error)))
            }
        }
    }

    private fun loadDirectory(targetUri: String, onSuccess: () -> Unit) {
        val profile = activeProfile ?: return
        val generation = beginRequest(getString(R.string.smb_loading))
        networkJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { manager.list(profile.id, targetUri) }
            }
            if (generation != requestGeneration) return@launch
            setBusy(false)
            result.onSuccess { entries ->
                onSuccess()
                currentUri = targetUri
                currentEntries = entries
                renderDirectory()
            }.onFailure { error ->
                showStatus(getString(R.string.smb_error_list_failed, safeErrorMessage(error)))
            }
        }
    }

    private fun openDirectory(entry: MediaEntry) {
        val previous = currentUri ?: return
        loadDirectory(entry.uri) { parentUris.addLast(previous) }
    }

    private fun navigateUpOrFinish() {
        val parent = parentUris.lastOrNull()
        if (parent == null) {
            finish()
            return
        }
        loadDirectory(parent) { parentUris.removeLast() }
    }

    private fun renderDirectory() {
        val profile = activeProfile ?: return
        val entries = sortSmbEntries(currentEntries, sortOrder)
        binding.smbConnectionPanel.visibility = View.GONE
        binding.smbNavigationRow.visibility = View.VISIBLE
        binding.smbCurrentFolderText.visibility = View.VISIBLE
        binding.smbEntriesList.visibility = View.VISIBLE
        binding.smbCurrentFolderText.text = getString(
            R.string.smb_current_folder,
            currentUri.orEmpty().removePrefix("smb://")
        )
        binding.smbBackButton.isEnabled = parentUris.isNotEmpty()
        binding.smbSortButton.text = getString(
            if (sortOrder == SmbSortOrder.NEWEST_FIRST) {
                R.string.smb_sort_newest_first
            } else {
                R.string.smb_sort_oldest_first
            }
        )
        showStatus(
            if (entries.isEmpty()) getString(R.string.smb_empty_folder)
            else getString(R.string.smb_entries_count, entries.size)
        )
        val loader = thumbnailLoader ?: SmbThumbnailLoader(
            manager = manager,
            profileId = profile.id,
            thumbnailSizePx = resources.getDimensionPixelSize(R.dimen.smb_thumbnail_size)
        ).also { thumbnailLoader = it }
        binding.smbEntriesList.adapter = SmbEntryAdapter(entries, loader) { entry ->
            if (entry.directory) openDirectory(entry) else openVideo(entry)
        }
    }

    private fun renderSavedProfiles() {
        binding.smbSavedProfilesContainer.removeAllViews()
        profileStore.loadAll().forEach { profile ->
            addButton(
                binding.smbSavedProfilesContainer,
                getString(R.string.smb_saved_profile, profile.displayName)
            ) {
                fillForm(profile)
                connect(profile, remember = true)
            }.setOnLongClickListener {
                confirmForget(profile)
                true
            }
        }
    }

    private fun fillForm(profile: SmbProfile) {
        binding.smbHostInput.setText(profile.host)
        binding.smbShareInput.setText(profile.share)
        binding.smbDomainInput.setText(profile.domain)
        binding.smbUsernameInput.setText(profile.username)
        binding.smbPasswordInput.setText(profile.password)
        binding.smbRememberAccount.isChecked = true
    }

    private fun confirmForget(profile: SmbProfile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.smb_forget_title)
            .setMessage(getString(R.string.smb_forget_message, profile.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.smb_forget) { _, _ ->
                profileStore.remove(profile.id)
                renderSavedProfiles()
            }
            .show()
    }

    private fun toggleSortOrder() {
        sortOrder = if (sortOrder == SmbSortOrder.NEWEST_FIRST) {
            SmbSortOrder.OLDEST_FIRST
        } else {
            SmbSortOrder.NEWEST_FIRST
        }
        renderDirectory()
    }

    private fun openVideo(entry: MediaEntry) {
        val profile = activeProfile ?: return
        val source = SourceDescriptor(
            original = entry.uri,
            normalized = entry.uri,
            type = SourceType.SMB_URI,
            displayName = entry.name,
            profileId = profile.id
        )
        lifecycleScope.launch {
            val probe = trackingManager.probeConnection()
            if (!probe.connected) {
                Toast.makeText(
                    this@SmbBrowserActivity,
                    R.string.toast_glasses_required,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val target = if (shouldLaunchTrackingSetupForSource(trackingManager.sessionState.value)) {
                TrackingSetupActivity::class.java
            } else {
                TrackingReadyActivity::class.java
            }
            startActivity(
                Intent(this@SmbBrowserActivity, target)
                    .putExtra(PlayerActivity.EXTRA_SOURCE, source)
            )
        }
    }

    private fun beginRequest(status: String): Long {
        requestGeneration += 1
        networkJob?.cancel()
        setBusy(true)
        showStatus(status)
        return requestGeneration
    }

    private fun setBusy(busy: Boolean) {
        binding.smbConnectButton.isEnabled = !busy
        binding.smbBackButton.isEnabled = !busy && parentUris.isNotEmpty()
        binding.smbSortButton.isEnabled = !busy
    }

    private fun showStatus(message: String) {
        binding.smbStatusText.text = message
    }

    private fun safeErrorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: getString(R.string.error_unknown)

    private fun addButton(
        container: LinearLayout,
        label: String,
        onClick: (View) -> Unit
    ): MaterialButton {
        val button = MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(onClick)
        }
        container.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.launcher_section_spacing) / 2 }
        )
        return button
    }
}
