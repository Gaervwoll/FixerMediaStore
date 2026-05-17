package com.fixermediastore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickFolder: Button
    private lateinit var btnScanFolder: Button
    private lateinit var btnScanGallery: Button
    private lateinit var btnPreflight: Button
    private lateinit var btnApply: Button
    private lateinit var btnCancel: Button
    private lateinit var cbSkipErrors: CheckBox
    private lateinit var cbSkipAlreadyCorrect: CheckBox
    private lateinit var cbUseExif: CheckBox
    private lateinit var tvFolder: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvLastError: TextView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnToggleFilters: TextView
    private lateinit var panelFilters: View
    private lateinit var btnToggleLog: TextView
    private lateinit var panelLog: View

    private lateinit var folderPreferences: FolderPreferences
    private lateinit var appPreferences: AppPreferences

    private var treeUri: Uri? = null
    private val entries = mutableListOf<ScanEntry>()
    private var searchQuery: String = ""

    private var workJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)
    private var lastErrorMessage: String? = null

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        onFolderPicked(uri)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        val allGranted = results.values.all { isGranted: Boolean -> isGranted }
        if (!allGranted) {
            Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickFolder = findViewById(R.id.btnPickFolder)
        btnScanFolder = findViewById(R.id.btnScanFolder)
        btnScanGallery = findViewById(R.id.btnScanGallery)
        btnPreflight = findViewById(R.id.btnPreflight)
        btnApply = findViewById(R.id.btnApply)
        btnCancel = findViewById(R.id.btnCancel)
        cbSkipErrors = findViewById(R.id.cbSkipErrors)
        cbSkipAlreadyCorrect = findViewById(R.id.cbSkipAlreadyCorrect)
        cbUseExif = findViewById(R.id.cbUseExif)
        tvFolder = findViewById(R.id.tvFolder)
        tvStats = findViewById(R.id.tvStats)
        tvLog = findViewById(R.id.tvLog)
        tvProgress = findViewById(R.id.tvProgress)
        tvLastError = findViewById(R.id.tvLastError)
        etSearch = findViewById(R.id.etSearch)
        progressBar = findViewById(R.id.progressBar)
        btnToggleFilters = findViewById(R.id.btnToggleFilters)
        panelFilters = findViewById(R.id.panelFilters)
        btnToggleLog = findViewById(R.id.btnToggleLog)
        panelLog = findViewById(R.id.panelLog)

        folderPreferences = FolderPreferences(this)
        appPreferences = AppPreferences(this)
        setupUi()
        restoreSavedFolder()
        offerRestoreSession()
    }

    private fun setupUi() {
        btnScanFolder.isEnabled = false
        btnPreflight.isEnabled = false
        btnApply.isEnabled = false
        btnCancel.isEnabled = false

        cbSkipErrors.isChecked = appPreferences.skipErrorsContinue
        cbSkipAlreadyCorrect.isChecked = appPreferences.skipAlreadyCorrect
        cbSkipErrors.setOnCheckedChangeListener { _, checked ->
            appPreferences.skipErrorsContinue = checked
        }
        cbSkipAlreadyCorrect.setOnCheckedChangeListener { _, checked ->
            appPreferences.skipAlreadyCorrect = checked
        }
        cbUseExif.isChecked = appPreferences.useExifWhenNoFilename
        cbUseExif.setOnCheckedChangeListener { _, checked ->
            appPreferences.useExifWhenNoFilename = checked
        }

        btnPickFolder.setOnClickListener { pickFolderLauncher.launch(null) }
        btnScanFolder.setOnClickListener { onScanFolderClicked() }
        btnScanGallery.setOnClickListener { onScanGalleryClicked() }
        btnPreflight.setOnClickListener { runPreflightOnly() }
        btnApply.setOnClickListener { onApplyClicked() }
        btnCancel.setOnClickListener { requestCancel() }

        btnToggleFilters.setOnClickListener { toggleFiltersPanel() }
        btnToggleLog.setOnClickListener { toggleLogPanel() }
        applyFiltersPanelExpanded(appPreferences.filtersPanelExpanded, animate = false)
        applyLogPanelExpanded(appPreferences.logPanelExpanded, animate = false)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                refreshUi()
            }
        })
    }

    private fun offerRestoreSession() {
        val saved = SessionPersistence.load(this) ?: return
        if (saved.entries.isEmpty()) return

        val applied = ScanLogFormatter.countApplied(saved.entries)
        val errors = ScanLogFormatter.countErrors(saved.entries)

        AlertDialog.Builder(this)
            .setTitle(R.string.restore_session_title)
            .setMessage(
                getString(
                    R.string.restore_session_message,
                    saved.entries.size,
                    applied,
                    errors
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                entries.clear()
                entries.addAll(saved.entries)
                lastErrorMessage = saved.lastError
                refreshUi()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                SessionPersistence.clear(this)
            }
            .show()
    }

    private fun requestCancel() {
        cancelRequested.set(true)
        btnCancel.isEnabled = false
        tvProgress.text = getString(R.string.apply_stopped_cancel)
    }

    private fun onFolderPicked(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        treeUri = uri
        folderPreferences.saveTreeUri(uri)
        tvFolder.text = DocumentsContract.getTreeDocumentId(uri)
        btnScanFolder.isEnabled = true
    }

    private fun restoreSavedFolder() {
        val uri = folderPreferences.loadTreeUri() ?: return
        treeUri = uri
        tvFolder.text = DocumentsContract.getTreeDocumentId(uri)
        btnScanFolder.isEnabled = true
    }

    private fun onScanFolderClicked() {
        val uri = treeUri
        if (uri == null) {
            Toast.makeText(this, R.string.no_folder, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensurePermissions()) return
        runFolderScan(uri)
    }

    private fun onScanGalleryClicked() {
        if (!ensurePermissions()) return
        runGalleryScan()
    }

    private fun ensurePermissions(): Boolean {
        if (MediaPermissions.isGranted(this)) return true
        permissionLauncher.launch(MediaPermissions.required())
        return false
    }

    private fun onApplyClicked() {
        if (ScanLogFormatter.countReady(entries) == 0) return

        startWork {
            val summary = withContext(Dispatchers.IO) {
                runPreflightOnReady()
            }
            SessionPersistence.save(applicationContext, entries)

            withContext(Dispatchers.Main) {
                if (summary.stillReady == 0) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.preflight_done,
                            summary.stillReady,
                            summary.skippedCorrect,
                            summary.markedErrors,
                            summary.resolvedNow
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshUi()
                    return@withContext
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.apply_confirm_title)
                    .setMessage(
                        getString(
                            R.string.apply_confirm_preflight,
                            summary.stillReady,
                            summary.skippedCorrect,
                            summary.markedErrors
                        )
                    )
                    .setPositiveButton(android.R.string.ok) { _, _ -> runApply() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun runPreflightOnly() {
        if (ScanLogFormatter.countPreflightable(entries) == 0) return
        startWork {
            val summary = withContext(Dispatchers.IO) { runPreflightOnReady() }
            SessionPersistence.save(applicationContext, entries)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.preflight_done,
                        summary.stillReady,
                        summary.skippedCorrect,
                        summary.markedErrors,
                        summary.resolvedNow
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun runPreflightOnReady(): PreflightSummary {
        withContext(Dispatchers.Main) {
            showProgressIndeterminate(getString(R.string.indexing_media_store))
        }
        return ApplyPreprocessor.prepare(
            applicationContext,
            entries,
            skipAlreadyCorrect = cbSkipAlreadyCorrect.isChecked,
            retryPreviousErrors = true
        )
    }

    private fun createScanProgressListener(): ScanProgressListener {
        return ScanProgressListener { processed, total, name ->
            if (processed % 25 == 0 || processed == total) {
                lifecycleScope.launch(Dispatchers.Main) {
                    updateProgress(processed, total, name, isApplying = false)
                }
            }
        }
    }

    private fun runFolderScan(uri: Uri) {
        startWork {
            showProgressIndeterminate(getString(R.string.scan_counting))
            val appContext = applicationContext
            val scanned = withContext(Dispatchers.IO) {
                MediaFileScanner.scan(
                    appContext,
                    uri,
                    appPreferences.useExifWhenNoFilename,
                    createScanProgressListener()
                )
            }
            entries.clear()
            entries.addAll(scanned)
            MediaStoreIndex.invalidate()
            SessionPersistence.save(appContext, entries)
            lastErrorMessage = null
        }
    }

    private fun runGalleryScan() {
        startWork {
            showProgressIndeterminate(getString(R.string.gallery_scan_started))
            val appContext = applicationContext
            val scanned = withContext(Dispatchers.IO) {
                MediaStoreScanner.scan(
                    appContext,
                    appPreferences.useExifWhenNoFilename,
                    createScanProgressListener()
                )
            }
            entries.clear()
            entries.addAll(scanned)
            MediaStoreIndex.invalidate()
            SessionPersistence.save(appContext, entries)
            lastErrorMessage = null
        }
    }

    private fun runApply() {
        if (!ensurePermissions()) return
        if (entries.none { it.status == ScanStatus.READY }) return

        val continueOnError = cbSkipErrors.isChecked

        startWork {
            val appContext = applicationContext
            var processed = 0
            var successCount = 0
            var errorCount = 0
            var stoppedByError = false

            withContext(Dispatchers.IO) {
                runPreflightOnReady()
                val toApply = entries.filter { it.status == ScanStatus.READY }
                val total = toApply.size

                for (entry in toApply) {
                    if (!isActive || cancelRequested.get()) break

                    val result = MetadataRestorer.restore(appContext, entry)
                    processed++

                    when (result) {
                        is RestoreResult.Success -> successCount++
                        is RestoreResult.Skipped -> { /* уже OK или пропуск */ }
                        is RestoreResult.Failed -> {
                            errorCount++
                            lastErrorMessage = result.errorMessage
                            if (continueOnError) {
                                SessionPersistence.save(
                                    appContext,
                                    entries,
                                    applyIndex = processed,
                                    lastError = result.errorMessage
                                )
                            } else {
                                stoppedByError = true
                                SessionPersistence.save(
                                    appContext,
                                    entries,
                                    applyIndex = processed,
                                    lastError = result.errorMessage
                                )
                                withContext(Dispatchers.Main) {
                                    showApplyErrorDialog(result.errorMessage, entry.displayName)
                                }
                                break
                            }
                        }
                    }

                    if (processed % 5 == 0 || processed == total) {
                        SessionPersistence.save(appContext, entries, applyIndex = processed)
                        withContext(Dispatchers.Main) {
                            updateProgress(processed, total, entry.displayName, isApplying = true)
                            refreshUiLight()
                        }
                    }
                }
            }

            SessionPersistence.save(applicationContext, entries, applyIndex = processed)

            withContext(Dispatchers.Main) {
                if (cancelRequested.get()) {
                    Toast.makeText(this@MainActivity, R.string.apply_stopped_cancel, Toast.LENGTH_LONG).show()
                } else if (!stoppedByError) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.apply_finished, successCount, errorCount),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startWork(block: suspend () -> Unit) {
        workJob?.cancel()
        cancelRequested.set(false)
        setBusy(true)

        workJob = lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                lastErrorMessage = "${e.javaClass.simpleName}: ${e.message}"
                SessionPersistence.save(applicationContext, entries, lastError = lastErrorMessage)
                withContext(Dispatchers.Main) {
                    showError(lastErrorMessage!!)
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.apply_confirm_title)
                        .setMessage(lastErrorMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    refreshUi()
                    setBusy(false)
                }
            }
        }
    }

    private fun showApplyErrorDialog(message: String, fileName: String) {
        showError(message)
        AlertDialog.Builder(this)
            .setTitle(R.string.apply_confirm_title)
            .setMessage(getString(R.string.apply_stopped_error, message, fileName))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showError(message: String) {
        lastErrorMessage = message
        tvLastError.visibility = View.VISIBLE
        tvLastError.text = getString(R.string.last_error_label, message)
    }

    private fun showProgressIndeterminate(message: String) {
        tvProgress.visibility = View.VISIBLE
        tvProgress.text = message
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
    }

    private fun updateProgress(processed: Int, total: Int, fileName: String?, isApplying: Boolean) {
        tvProgress.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        if (total <= 0) {
            progressBar.isIndeterminate = true
            tvProgress.text = getString(R.string.scan_counting)
            return
        }

        progressBar.isIndeterminate = false
        progressBar.max = total
        progressBar.progress = processed.coerceIn(0, total)

        tvProgress.text = when {
            isApplying && fileName.isNullOrBlank() ->
                getString(R.string.apply_progress, processed, total)
            isApplying ->
                getString(R.string.scan_progress_file, processed, total, fileName)
            fileName.isNullOrBlank() ->
                getString(R.string.scan_progress, processed, total)
            else ->
                getString(R.string.scan_progress_file, processed, total, fileName)
        }
    }

    private fun hideProgress() {
        tvProgress.visibility = View.GONE
        progressBar.visibility = View.GONE
        progressBar.isIndeterminate = false
        progressBar.progress = 0
    }

    /** Обновление без тяжёлого лога — во время apply */
    private fun refreshUiLight() {
        tvStats.text = getString(
            R.string.stats_format,
            entries.size,
            ScanLogFormatter.countReady(entries),
            ScanLogFormatter.countApplied(entries),
            ScanLogFormatter.countSkipped(entries),
            ScanLogFormatter.countSkippedCorrect(entries),
            ScanLogFormatter.countFromExif(entries),
            ScanLogFormatter.countSkippedNoDate(entries),
            ScanLogFormatter.countErrors(entries)
        )
        val hasReady = ScanLogFormatter.countReady(entries) > 0
        val canPreflight = ScanLogFormatter.countPreflightable(entries) > 0
        btnApply.isEnabled = hasReady
        btnPreflight.isEnabled = canPreflight
    }

    private fun refreshUi() {
        tvLog.text = ScanLogFormatter.format(this, entries, searchQuery)
        refreshUiLight()
        updateCollapsibleHeaders()
        lastErrorMessage?.let { showError(it) } ?: run {
            tvLastError.visibility = View.GONE
        }
    }

    private fun toggleFiltersPanel() {
        applyFiltersPanelExpanded(!appPreferences.filtersPanelExpanded, animate = true)
    }

    private fun toggleLogPanel() {
        applyLogPanelExpanded(!appPreferences.logPanelExpanded, animate = true)
    }

    private fun applyFiltersPanelExpanded(expanded: Boolean, animate: Boolean) {
        appPreferences.filtersPanelExpanded = expanded
        panelFilters.visibility = if (expanded) View.VISIBLE else View.GONE
        btnToggleFilters.setText(
            if (expanded) R.string.section_filters_expanded else R.string.section_filters_collapsed
        )
        if (animate) {
            panelFilters.alpha = 0f
            panelFilters.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun applyLogPanelExpanded(expanded: Boolean, animate: Boolean) {
        appPreferences.logPanelExpanded = expanded
        panelLog.visibility = if (expanded) View.VISIBLE else View.GONE
        updateCollapsibleHeaders()
        if (animate) {
            panelLog.alpha = 0f
            panelLog.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun updateCollapsibleHeaders() {
        btnToggleLog.text = if (appPreferences.logPanelExpanded) {
            getString(R.string.section_log_expanded)
        } else {
            getString(R.string.section_log_collapsed, entries.size)
        }
    }

    private fun setBusy(busy: Boolean) {
        btnPickFolder.isEnabled = !busy
        btnScanFolder.isEnabled = !busy && treeUri != null
        btnScanGallery.isEnabled = !busy
        val hasReady = ScanLogFormatter.countReady(entries) > 0
        val canPreflight = ScanLogFormatter.countPreflightable(entries) > 0
        btnApply.isEnabled = !busy && hasReady
        btnPreflight.isEnabled = !busy && canPreflight
        btnCancel.isEnabled = busy
    }
}
