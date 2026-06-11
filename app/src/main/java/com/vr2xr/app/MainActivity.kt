package com.vr2xr.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.vr2xr.R
import com.vr2xr.databinding.ActivityMainBinding
import com.vr2xr.source.IntentIngestor
import com.vr2xr.source.SourceDescriptor
import com.vr2xr.source.SourceResolver
import com.vr2xr.tracking.OneXrConnectionProbe
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private enum class ConnectionStatusUi(
        val labelResId: Int,
        val indicatorColorResId: Int
    ) {
        CHECKING(R.string.xreal_status_checking, R.color.xreal_status_checking),
        CONNECTED(R.string.xreal_status_connected, R.color.xreal_status_connected),
        NOT_CONNECTED(R.string.xreal_status_not_connected, R.color.xreal_status_not_connected),
    }

    private lateinit var binding: ActivityMainBinding
    private val resolver = SourceResolver()
    private val errors by lazy { ErrorUiController(this) }
    private val trackingManager by lazy { (application as Vr2xrApplication).trackingSessionManager }
    private val playbackSession by lazy { (application as Vr2xrApplication).playbackSessionOwner }
    private var connectionProbeJob: Job? = null
    private var pendingResumeRequest = false
    private var currentConnectionStatus = ConnectionStatusUi.CHECKING
    private var startupCalibrationLaunched = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }
        resolver.resolveUri(this, uri, persistPermission = true)
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: getString(R.string.error_open_selected_file)) }
    }

    private val openCifsTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val persistResult = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        if (persistResult.isFailure) {
            errors.show(getString(R.string.saf_error_permission_persist_failed))
            return@registerForActivityResult
        }
        saveLastCifsTreeUri(uri)
        launchSafDocumentBrowser(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openFileButton.setOnClickListener {
            openDocument.launch(arrayOf("video/*"))
        }
        binding.openCifsFolderButton.setOnClickListener {
            openCifsTree.launch(readLastCifsTreeUri())
        }

        binding.openUrlButton.setOnClickListener {
            val raw = binding.urlInput.text?.toString().orEmpty()
            resolver.resolveUrl(raw)
                .onSuccess(::handleResolvedSource)
                .onFailure { errors.show(it.message ?: getString(R.string.error_invalid_url)) }
        }
        binding.requirementsHelpButton.setOnClickListener {
            showRequirementsModal()
        }

        renderConnectionStatusUi(ConnectionStatusUi.CHECKING)
        setRuntimeStatus(getString(R.string.status_glasses_required))
        pendingResumeRequest = isExplicitResumeRequest(intent)
        maybeHandleInboundIntent(intent)
        maybeResumeActivePlaybackSession(consumeOnMiss = false)
    }

    override fun onStart() {
        super.onStart()
        startConnectionProbeLoop()
        maybeResumeActivePlaybackSession(consumeOnMiss = true)
    }

    override fun onStop() {
        super.onStop()
        connectionProbeJob?.cancel()
        connectionProbeJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingResumeRequest = isExplicitResumeRequest(intent)
        maybeHandleInboundIntent(intent)
        maybeResumeActivePlaybackSession(consumeOnMiss = true)
    }

    private fun maybeHandleInboundIntent(intent: Intent?) {
        if (intent == null) return
        val ingestorResult = IntentIngestor(resolver).ingest(this, intent) ?: return
        ingestorResult
            .onSuccess(::handleResolvedSource)
            .onFailure { errors.show(it.message ?: getString(R.string.error_unsupported_launch_intent)) }
    }

    private fun handleResolvedSource(source: SourceDescriptor) {
        lifecycleScope.launch {
            val probe = trackingManager.probeConnection()
            renderConnectionStatus(probe)
            if (probe.connected) {
                if (shouldLaunchTrackingSetupForSource(trackingManager.sessionState.value)) {
                    launchTrackingSetup(source)
                } else {
                    launchTrackingReady(source)
                }
            } else {
                setRuntimeStatus(getString(R.string.status_glasses_required))
                Toast.makeText(this@MainActivity, R.string.toast_glasses_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchPlayer(source: SourceDescriptor, resumeExisting: Boolean = false) {
        val intent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_SOURCE, source)
            .putExtra(PlayerActivity.EXTRA_RESUME_EXISTING, resumeExisting)
        if (resumeExisting) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun launchTrackingSetup(source: SourceDescriptor?) {
        val intent = Intent(this, TrackingSetupActivity::class.java)
        if (source != null) {
            intent.putExtra(PlayerActivity.EXTRA_SOURCE, source)
        }
        startActivity(intent)
    }

    private fun launchTrackingReady(source: SourceDescriptor) {
        startActivity(
            Intent(this, TrackingReadyActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_SOURCE, source)
        )
    }

    private fun launchSafDocumentBrowser(treeUri: Uri) {
        startActivity(
            Intent(this, SafDocumentBrowserActivity::class.java)
                .putExtra(SafDocumentBrowserActivity.EXTRA_TREE_URI, treeUri.toString())
        )
    }

    private fun readLastCifsTreeUri(): Uri? {
        val raw = getSharedPreferences(CIFS_TREE_PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_CIFS_TREE_URI, null)
        return raw?.let(Uri::parse)
    }

    private fun saveLastCifsTreeUri(treeUri: Uri) {
        getSharedPreferences(CIFS_TREE_PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CIFS_TREE_URI, treeUri.toString())
            .apply()
    }

    private fun startConnectionProbeLoop() {
        connectionProbeJob?.cancel()
        connectionProbeJob = lifecycleScope.launch {
            while (isActive) {
                val probe = trackingManager.probeConnection()
                renderConnectionStatus(probe)
                maybeLaunchStartupCalibration(probe)
                delay(2000L)
            }
        }
    }

    private fun maybeLaunchStartupCalibration(probe: OneXrConnectionProbe) {
        if (
            startupCalibrationLaunched ||
            pendingResumeRequest ||
            !probe.connected ||
            isFinishing
        ) {
            return
        }
        if (!shouldLaunchTrackingSetupForSource(trackingManager.sessionState.value)) {
            return
        }
        startupCalibrationLaunched = true
        launchTrackingSetup(source = null)
    }

    private fun maybeResumeActivePlaybackSession(consumeOnMiss: Boolean) {
        if (!pendingResumeRequest) {
            return
        }
        val activeSource = playbackSession.state.value.source
        if (activeSource == null) {
            if (consumeOnMiss) {
                pendingResumeRequest = false
            }
            return
        }
        pendingResumeRequest = false
        launchPlayer(activeSource, resumeExisting = true)
        finish()
    }

    private fun isExplicitResumeRequest(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        return PlaybackResumeContract.isResumeRequest(
            action = intent.action,
            resumeRequested = intent.getBooleanExtra(
                PlaybackResumeContract.EXTRA_RESUME_REQUESTED,
                false
            )
        )
    }

    private fun setRuntimeStatus(message: String?) {
        if (message.isNullOrBlank()) {
            binding.statusText.text = ""
            binding.statusText.visibility = View.GONE
            return
        }
        binding.statusText.text = message
        binding.statusText.visibility = View.VISIBLE
    }

    private fun showRequirementsModal() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.requirements_modal_title)
            .setMessage(R.string.requirements_modal_message)
            .setPositiveButton(R.string.requirements_modal_action, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.catppuccin_mocha_blue))
    }

    private fun renderConnectionStatusUi(nextStatus: ConnectionStatusUi) {
        val animateConnectedTransition =
            currentConnectionStatus == ConnectionStatusUi.NOT_CONNECTED &&
                nextStatus == ConnectionStatusUi.CONNECTED

        currentConnectionStatus = nextStatus
        binding.xrealStatusText.text = getString(nextStatus.labelResId)
        binding.xrealStatusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, nextStatus.indicatorColorResId)
        )

        if (animateConnectedTransition) {
            playConnectedMicroAnimation()
            return
        }
        resetStatusAnimations()
    }

    private fun resetStatusAnimations() {
        binding.xrealStatusDot.scaleX = 1f
        binding.xrealStatusDot.scaleY = 1f
        binding.xrealStatusDot.alpha = 1f
        binding.xrealStatusIcon.translationY = 0f
        binding.xrealStatusIcon.scaleX = 1f
        binding.xrealStatusIcon.scaleY = 1f
        binding.xrealStatusText.alpha = 1f
    }

    private fun playConnectedMicroAnimation() {
        val iconNudge = resources.getDimension(R.dimen.launcher_icon_nudge_distance)
        val dotScaleX = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.SCALE_X, 1f, 1.65f, 1f)
        val dotScaleY = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.SCALE_Y, 1f, 1.65f, 1f)
        val dotFade = ObjectAnimator.ofFloat(binding.xrealStatusDot, View.ALPHA, 0.5f, 1f)
        val textFade = ObjectAnimator.ofFloat(binding.xrealStatusText, View.ALPHA, 0.2f, 1f)
        val iconNudgeAnimator = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.TRANSLATION_Y, 0f, -iconNudge, 0f)
        val iconScaleX = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.SCALE_X, 1f, 1.1f, 1f)
        val iconScaleY = ObjectAnimator.ofFloat(binding.xrealStatusIcon, View.SCALE_Y, 1f, 1.1f, 1f)
        AnimatorSet().apply {
            duration = 420L
            interpolator = FastOutSlowInInterpolator()
            playTogether(dotScaleX, dotScaleY, dotFade, textFade, iconNudgeAnimator, iconScaleX, iconScaleY)
            start()
        }
    }

    private fun renderConnectionStatus(probe: OneXrConnectionProbe) {
        val nextStatus = if (probe.connected) {
            ConnectionStatusUi.CONNECTED
        } else {
            ConnectionStatusUi.NOT_CONNECTED
        }
        renderConnectionStatusUi(nextStatus)
    }

    companion object {
        private const val CIFS_TREE_PREFS_NAME = "cifs_tree"
        private const val KEY_LAST_CIFS_TREE_URI = "last_tree_uri"
    }
}
