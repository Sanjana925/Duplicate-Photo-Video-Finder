package com.sanjana.duplicatefinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sanjana.duplicatefinder.database.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView

    private lateinit var scanAllButton: MaterialButton
    private lateinit var selectFoldersButton: MaterialButton
    private lateinit var scanSelectedButton: MaterialButton
    private lateinit var excludeFoldersButton: MaterialButton
    private lateinit var clearExcludedFoldersButton: MaterialButton

    private lateinit var scanningProgressContainer: View
    private lateinit var scanningStageText: TextView
    private lateinit var scanningProgressText: TextView
    private lateinit var scanningProgressBar: ProgressBar
    private lateinit var cancelScanButton: MaterialButton

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var selectedFoldersText: TextView
    private lateinit var excludedFoldersText: TextView

    private lateinit var photoCountText: TextView
    private lateinit var videoCountText: TextView
    private lateinit var totalCountText: TextView

    private val selectedFolderUris =
        mutableListOf<Uri>()

    private val excludedFolderUris =
        mutableListOf<Uri>()

    private var scanJob: Job? = null

    private lateinit var database: AppDatabase

    /*
     * Remembers what the user requested before Android
     * shows the permission dialog.
     *
     * true  = Scan All
     * false = Scan Selected Folders
     */
    private var pendingScanAll: Boolean? = null

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val imageGranted =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    permissions[
                        Manifest.permission.READ_MEDIA_IMAGES
                    ] == true
                } else {
                    false
                }

            val videoGranted =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    permissions[
                        Manifest.permission.READ_MEDIA_VIDEO
                    ] == true
                } else {
                    false
                }

            val legacyGranted =
                if (
                    Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    permissions[
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ] == true
                } else {
                    false
                }

            val hasRequiredPermission =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    imageGranted && videoGranted
                } else {
                    legacyGranted
                }

            if (hasRequiredPermission) {

                val scanAll =
                    pendingScanAll ?: true

                pendingScanAll = null

                startScan(
                    scanAll
                )

            } else {

                pendingScanAll = null

                Toast.makeText(
                    this,
                    "Photo and video permission is required to scan your media.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val folderPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            try {

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: Exception) {
            }

            if (!selectedFolderUris.contains(uri)) {

                selectedFolderUris.add(uri)

                updateSelectedFoldersText()

                Toast.makeText(
                    this,
                    "Folder added.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                Toast.makeText(
                    this,
                    "Folder already selected.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val excludeFolderPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            try {

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: Exception) {
            }

            if (!excludedFolderUris.contains(uri)) {

                excludedFolderUris.add(uri)

                updateExcludedFoldersText()

                Toast.makeText(
                    this,
                    "Folder excluded.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                Toast.makeText(
                    this,
                    "Folder already excluded.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        database =
            AppDatabase.getInstance(
                this
            )

        bindViews()

        setupButtons()

        updateSelectedFoldersText()
        updateExcludedFoldersText()
    }

    private fun bindViews() {

        titleText =
            findViewById(
                R.id.titleText
            )

        subtitleText =
            findViewById(
                R.id.subtitleText
            )

        scanAllButton =
            findViewById(
                R.id.scanAllButton
            )

        selectFoldersButton =
            findViewById(
                R.id.selectFoldersButton
            )

        scanSelectedButton =
            findViewById(
                R.id.scanSelectedButton
            )

        excludeFoldersButton =
            findViewById(
                R.id.excludeFoldersButton
            )

        clearExcludedFoldersButton =
            findViewById(
                R.id.clearExcludedFoldersButton
            )

        scanningProgressContainer =
            findViewById(
                R.id.scanningProgressContainer
            )

        scanningStageText =
            findViewById(
                R.id.scanningStageText
            )

        scanningProgressText =
            findViewById(
                R.id.scanningProgressText
            )

        scanningProgressBar =
            findViewById(
                R.id.scanningProgressBar
            )

        cancelScanButton =
            findViewById(
                R.id.cancelScanButton
            )

        progressBar =
            findViewById(
                R.id.progressBar
            )

        statusText =
            findViewById(
                R.id.statusText
            )

        selectedFoldersText =
            findViewById(
                R.id.selectedFoldersText
            )

        excludedFoldersText =
            findViewById(
                R.id.excludedFoldersText
            )

        photoCountText =
            findViewById(
                R.id.photoCountText
            )

        videoCountText =
            findViewById(
                R.id.videoCountText
            )

        totalCountText =
            findViewById(
                R.id.totalCountText
            )
    }

    private fun setupButtons() {

        scanAllButton.setOnClickListener {

            if (scanJob?.isActive == true) {
                return@setOnClickListener
            }

            checkPermissionsAndScan(
                true
            )
        }

        selectFoldersButton.setOnClickListener {

            if (scanJob?.isActive == true) {
                return@setOnClickListener
            }

            folderPickerLauncher.launch(
                null
            )
        }

        scanSelectedButton.setOnClickListener {

            if (scanJob?.isActive == true) {
                return@setOnClickListener
            }

            if (selectedFolderUris.isEmpty()) {

                Toast.makeText(
                    this,
                    "Select at least one folder first.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            checkPermissionsAndScan(
                false
            )
        }

        excludeFoldersButton.setOnClickListener {

            if (scanJob?.isActive == true) {
                return@setOnClickListener
            }

            excludeFolderPickerLauncher.launch(
                null
            )
        }

        clearExcludedFoldersButton.setOnClickListener {

            if (scanJob?.isActive == true) {
                return@setOnClickListener
            }

            excludedFolderUris.clear()

            updateExcludedFoldersText()

            Toast.makeText(
                this,
                "Excluded folders cleared.",
                Toast.LENGTH_SHORT
            ).show()
        }

        cancelScanButton.setOnClickListener {

            scanJob?.cancel()

            setScanningUi(
                false
            )

            statusText.text =
                "Scan cancelled."
        }
    }

    private fun checkPermissionsAndScan(
        scanAll: Boolean
    ) {

        pendingScanAll =
            scanAll

        val permissions =
            requiredPermissions()

        val missingPermissions =
            permissions.filter { permission ->

                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isEmpty()) {

            pendingScanAll = null

            startScan(
                scanAll
            )

        } else {

            permissionLauncher.launch(
                missingPermissions.toTypedArray()
            )
        }
    }

    private fun requiredPermissions(): List<String> {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )

        } else {

            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun startScan(
        scanAll: Boolean
    ) {

        if (scanJob?.isActive == true) {
            return
        }

        if (
            !scanAll &&
            selectedFolderUris.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Select at least one folder first.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        setScanningUi(
            true
        )

        scanningProgressBar.progress = 0

        scanningProgressText.text =
            "Scanning..."

        scanningStageText.text =
            if (scanAll) {
                "Preparing full scan..."
            } else {
                "Preparing selected-folder scan..."
            }

        statusText.text =
            if (scanAll) {
                "Scanning all media..."
            } else {
                "Scanning selected folders..."
            }

        val selectedPaths =
            if (scanAll) {
                emptyList()
            } else {
                selectedFolderUris
                    .map {
                        getTreeRelativePath(it)
                    }
                    .filter {
                        it.isNotBlank()
                    }
            }

        val excludedPaths =
            excludedFolderUris
                .map {
                    getTreeRelativePath(it)
                }
                .filter {
                    it.isNotBlank()
                }

        if (
            !scanAll &&
            selectedPaths.isEmpty()
        ) {

            setScanningUi(
                false
            )

            Toast.makeText(
                this,
                "Unable to read the selected folder path.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        scanJob =
            lifecycleScope.launch {

                try {

                    withContext(
                        Dispatchers.IO
                    ) {

                        ScanManager.scan(
                            context = this@MainActivity,
                            selectedRelativePaths = selectedPaths,
                            excludedRelativePaths = excludedPaths,
                            scanAll = scanAll,
                            onProgress = { progress ->

                                runOnUiThread {

                                    updateProgress(
                                        progress
                                    )
                                }
                            },
                            isCancelled = {
                                !isActive
                            }
                        )
                    }

                    if (!isActive) {
                        return@launch
                    }

                    setScanningUi(
                        false
                    )

                    showScanResult()

                } catch (
                    exception: CancellationException
                ) {

                    setScanningUi(
                        false
                    )

                    statusText.text =
                        "Scan cancelled."

                } catch (
                    exception: Exception
                ) {

                    setScanningUi(
                        false
                    )

                    statusText.text =
                        "Scan failed."

                    Toast.makeText(
                        this@MainActivity,
                        "Scan failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun updateProgress(
        progress: String
    ) {

        scanningStageText.text =
            progress

        /*
         * Our ScanManager currently reports text progress
         * rather than a numeric percentage.
         */
        scanningProgressText.text =
            "Working..."

        scanningProgressBar.isIndeterminate =
            true
    }

    private fun showScanResult() {

        /*
         * Read the final counts directly from Room.
         */
        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            val photoCount =
                database.mediaDao()
                    .getPhotoCount()

            val videoCount =
                database.mediaDao()
                    .getVideoCount()

            val totalCount =
                database.mediaDao()
                    .getTotalCount()

            withContext(
                Dispatchers.Main
            ) {

                photoCountText.text =
                    "Photos: $photoCount"

                videoCountText.text =
                    "Videos: $videoCount"

                totalCountText.text =
                    "Total: $totalCount"

                statusText.text =
                    "Scan completed."

                val intent =
                    Intent(
                        this@MainActivity,
                        DuplicateResultsActivity::class.java
                    )

                startActivity(
                    intent
                )
            }
        }
    }

    private fun setScanningUi(
        scanning: Boolean
    ) {

        scanningProgressContainer.visibility =
            if (scanning) {
                View.VISIBLE
            } else {
                View.GONE
            }

        scanningProgressBar.isIndeterminate =
            scanning

        scanAllButton.isEnabled =
            !scanning

        selectFoldersButton.isEnabled =
            !scanning

        scanSelectedButton.isEnabled =
            !scanning &&
                    selectedFolderUris.isNotEmpty()

        excludeFoldersButton.isEnabled =
            !scanning

        clearExcludedFoldersButton.isEnabled =
            !scanning &&
                    excludedFolderUris.isNotEmpty()
    }

    private fun updateSelectedFoldersText() {

        if (selectedFolderUris.isEmpty()) {

            selectedFoldersText.text =
                getString(
                    R.string.no_folders_selected
                )

            scanSelectedButton.isEnabled =
                false

            return
        }

        selectedFoldersText.text =
            getString(
                R.string.folders_selected,
                selectedFolderUris.size
            )

        scanSelectedButton.isEnabled =
            scanJob?.isActive != true
    }

    private fun updateExcludedFoldersText() {

        if (excludedFolderUris.isEmpty()) {

            excludedFoldersText.text =
                "No excluded folders."

            clearExcludedFoldersButton.isEnabled =
                false

            return
        }

        val folderNames =
            excludedFolderUris.mapIndexed {
                    index,
                    uri ->

                val path =
                    getTreeRelativePath(
                        uri
                    )

                val name =
                    path
                        .trim('/')
                        .substringAfterLast('/')
                        .ifBlank {
                            path.ifBlank {
                                "Selected folder"
                            }
                        }

                "${index + 1}. $name"
            }

        excludedFoldersText.text =
            buildString {

                append(
                    "Excluded folders: "
                )

                append(
                    excludedFolderUris.size
                )

                append(
                    "\n"
                )

                append(
                    folderNames.joinToString(
                        "\n"
                    )
                )
            }

        clearExcludedFoldersButton.isEnabled =
            scanJob?.isActive != true
    }

    private fun getTreeRelativePath(
        treeUri: Uri
    ): String {

        return try {

            val documentId =
                DocumentsContract.getTreeDocumentId(
                    treeUri
                )

            if (
                documentId.startsWith(
                    "primary:",
                    ignoreCase = true
                )
            ) {

                documentId
                    .substringAfter(':')
                    .trim('/')

            } else {

                documentId
                    .substringAfterLast(':')
                    .trim('/')
            }

        } catch (_: Exception) {

            ""
        }
    }

    override fun onDestroy() {

        scanJob?.cancel()

        super.onDestroy()
    }
}
