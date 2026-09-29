package com.sanjana.duplicatefinder

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var scanAllButton: Button
    private lateinit var selectFoldersButton: Button
    private lateinit var scanSelectedButton: Button

    private lateinit var progressBar: ProgressBar

    private lateinit var scanningProgressContainer: View
    private lateinit var scanningStageText: TextView
    private lateinit var scanningProgressText: TextView
    private lateinit var scanningProgressBar: ProgressBar
    private lateinit var cancelScanButton: Button

    private lateinit var statusText: TextView
    private lateinit var selectedFoldersText: TextView
    private lateinit var photoCountText: TextView
    private lateinit var videoCountText: TextView
    private lateinit var totalCountText: TextView

    private val selectedFolderPaths =
        mutableListOf<String>()

    private var pendingScanSelectedOnly =
        false

    private var scanJob: Job? =
        null

    private val folderPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != RESULT_OK) {
                return@registerForActivityResult
            }

            val treeUri =
                result.data?.data
                    ?: return@registerForActivityResult

            try {

                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: SecurityException) {
            }

            val folderPath =
                getFolderPathFromTreeUri(
                    treeUri
                )

            if (folderPath.isNotEmpty()) {

                if (
                    !selectedFolderPaths.contains(
                        folderPath
                    )
                ) {

                    selectedFolderPaths.add(
                        folderPath
                    )

                    updateSelectedFoldersUi()

                    statusText.text =
                        getString(
                            R.string.folder_added,
                            folderPath
                        )
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {

                    permissions[
                        Manifest.permission.READ_MEDIA_IMAGES
                    ] == true ||
                            permissions[
                                Manifest.permission.READ_MEDIA_VIDEO
                            ] == true

                } else {

                    permissions.values.any {
                        it
                    }
                }

            if (granted) {

                startScan(
                    pendingScanSelectedOnly
                )

            } else {

                statusText.text =
                    getString(
                        R.string.permission_required
                    )
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

        progressBar =
            findViewById(
                R.id.progressBar
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

        statusText =
            findViewById(
                R.id.statusText
            )

        selectedFoldersText =
            findViewById(
                R.id.selectedFoldersText
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

        scanAllButton.setOnClickListener {

            checkPermissionsAndScan(
                selectedOnly = false
            )
        }

        selectFoldersButton.setOnClickListener {

            openFolderPicker()
        }

        scanSelectedButton.setOnClickListener {

            if (
                selectedFolderPaths.isNotEmpty()
            ) {

                checkPermissionsAndScan(
                    selectedOnly = true
                )
            }
        }

        cancelScanButton.setOnClickListener {

            cancelCurrentScan()
        }

        updateSelectedFoldersUi()
    }

    private fun openFolderPicker() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE
            )

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        folderPickerLauncher.launch(
            intent
        )
    }

    private fun updateSelectedFoldersUi() {

        if (
            selectedFolderPaths.isEmpty()
        ) {

            selectedFoldersText.text =
                getString(
                    R.string.no_folders_selected
                )

            scanSelectedButton.isEnabled =
                false

            return
        }

        val text =
            buildString {

                append(
                    getString(
                        R.string.folders_selected,
                        selectedFolderPaths.size
                    )
                )

                append("\n\n")

                selectedFolderPaths.forEachIndexed {
                        index,
                        path ->

                    append(index + 1)
                    append(". ")
                    append(path)
                    append("\n")
                }
            }

        selectedFoldersText.text =
            text

        scanSelectedButton.isEnabled =
            true
    }

    private fun checkPermissionsAndScan(
        selectedOnly: Boolean
    ) {

        pendingScanSelectedOnly =
            selectedOnly

        val permissions: Array<String>

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            permissions =
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )

        } else {

            permissions =
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
        }

        val allGranted =
            permissions.all { permission ->

                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) ==
                        PackageManager.PERMISSION_GRANTED
            }

        if (allGranted) {

            startScan(
                selectedOnly
            )

        } else {

            permissionLauncher.launch(
                permissions
            )
        }
    }

    private fun startScan(
        selectedOnly: Boolean
    ) {

        if (
            scanJob?.isActive == true
        ) {
            return
        }

        scanAllButton.isEnabled =
            false

        selectFoldersButton.isEnabled =
            false

        scanSelectedButton.isEnabled =
            false

        progressBar.visibility =
            View.GONE

        scanningProgressContainer.visibility =
            View.VISIBLE

        scanningProgressBar.progress =
            0

        scanningProgressText.text =
            "0%"

        scanningStageText.text =
            "Preparing scan..."

        cancelScanButton.isEnabled =
            true

        statusText.text =
            getString(
                R.string.status_scanning
            )

        scanJob =
            lifecycleScope.launch {

                try {

                    val result =
                        withContext(
                            Dispatchers.IO
                        ) {

                            scanAndFindDuplicates(
                                selectedOnly
                            )
                        }

                    showScanFinishedState()

                    photoCountText.text =
                        getString(
                            R.string.photos_count,
                            result.photos.size
                        )

                    videoCountText.text =
                        getString(
                            R.string.videos_count,
                            result.videos.size
                        )

                    totalCountText.text =
                        getString(
                            R.string.total_count,
                            result.photos.size +
                                    result.videos.size
                        )

                    statusText.text =
                        buildResultMessage(
                            result
                        )

                    if (
                        result.photoDuplicateGroups.isNotEmpty() ||
                        result.videoDuplicateGroups.isNotEmpty()
                    ) {

                        openDuplicateResults(
                            result
                        )
                    }

                } catch (
                    cancellation: CancellationException
                ) {

                    showScanFinishedState()

                    statusText.text =
                        "Scan cancelled."

                    scanningStageText.text =
                        "Scan cancelled"

                    scanningProgressText.text =
                        "Cancelled"

                    throw cancellation

                } catch (
                    exception: Exception
                ) {

                    showScanFinishedState()

                    statusText.text =
                        "Scan failed: ${exception.message ?: "Unknown error"}"

                    scanningStageText.text =
                        "Scan failed"
                }
            }
    }

    private fun cancelCurrentScan() {

        if (
            scanJob?.isActive != true
        ) {
            return
        }

        cancelScanButton.isEnabled =
            false

        scanningStageText.text =
            "Cancelling scan..."

        scanningProgressText.text =
            "Please wait..."

        statusText.text =
            "Cancelling scan..."

        scanJob?.cancel()
    }

    private fun showScanFinishedState() {

        scanAllButton.isEnabled =
            true

        selectFoldersButton.isEnabled =
            true

        scanSelectedButton.isEnabled =
            selectedFolderPaths.isNotEmpty()

        cancelScanButton.isEnabled =
            false

        scanningProgressContainer.visibility =
            View.GONE

        progressBar.visibility =
            View.GONE
    }

    private suspend fun updateScanProgress(
        stage: String,
        current: Int,
        total: Int
    ) {

        val safeTotal =
            maxOf(
                total,
                1
            )

        val percentage =
            (
                    current.toDouble() /
                            safeTotal.toDouble() *
                            100.0
                    )
                .toInt()
                .coerceIn(
                    0,
                    100
                )

        withContext(
            Dispatchers.Main
        ) {

            if (
                !isFinishing &&
                !isDestroyed
            ) {

                scanningStageText.text =
                    stage

                scanningProgressBar.progress =
                    percentage

                scanningProgressText.text =
                    "$percentage%  ($current / $total)"
            }
        }
    }

    private suspend fun updateStatusFromBackground(
        message: String
    ) {

        withContext(
            Dispatchers.Main
        ) {

            if (
                !isFinishing &&
                !isDestroyed
            ) {

                statusText.text =
                    message
            }
        }
    }

    private fun openDuplicateResults(
        result: ScanResult
    ) {

        val photoGroups =
            ArrayList<ArrayList<DuplicateResultsActivity.DuplicateItem>>()

        result.photoDuplicateGroups.forEach { group ->

            val convertedGroup =
                ArrayList<DuplicateResultsActivity.DuplicateItem>()

            group.forEach { mediaFile ->

                convertedGroup.add(

                    DuplicateResultsActivity.DuplicateItem(

                        name =
                            mediaFile.name,

                        relativePath =
                            mediaFile.relativePath,

                        size =
                            mediaFile.size,

                        width =
                            mediaFile.width,

                        height =
                            mediaFile.height,

                        sha256 =
                            mediaFile.sha256,

                        uri =
                            mediaFile.uri
                    )
                )
            }

            photoGroups.add(
                convertedGroup
            )
        }

        val videoGroups =
            ArrayList<ArrayList<DuplicateResultsActivity.DuplicateItem>>()

        result.videoDuplicateGroups.forEach { group ->

            val convertedGroup =
                ArrayList<DuplicateResultsActivity.DuplicateItem>()

            group.forEach { mediaFile ->

                convertedGroup.add(

                    DuplicateResultsActivity.DuplicateItem(

                        name =
                            mediaFile.name,

                        relativePath =
                            mediaFile.relativePath,

                        size =
                            mediaFile.size,

                        width =
                            mediaFile.width,

                        height =
                            mediaFile.height,

                        sha256 =
                            mediaFile.sha256,

                        uri =
                            mediaFile.uri
                    )
                )
            }

            videoGroups.add(
                convertedGroup
            )
        }

        val intent =
            Intent(
                this,
                DuplicateResultsActivity::class.java
            )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_PHOTO_GROUPS,
            photoGroups
        )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_VIDEO_GROUPS,
            videoGroups
        )

        startActivity(
            intent
        )
    }

    private suspend fun scanAndFindDuplicates(
        selectedOnly: Boolean
    ): ScanResult {

        currentCoroutineContext().ensureActive()

        updateStatusFromBackground(
            "Reading photos..."
        )

        withContext(
            Dispatchers.Main
        ) {

            scanningStageText.text =
                "Reading photos..."

            scanningProgressBar.progress =
                0

            scanningProgressText.text =
                "0%"
        }

        val photos =
            mutableListOf<MediaFile>()

        val videos =
            mutableListOf<MediaFile>()

        scanPhotos(
            photos,
            selectedOnly
        )

        currentCoroutineContext().ensureActive()

        withContext(
            Dispatchers.Main
        ) {

            scanningStageText.text =
                "Reading videos..."

            scanningProgressBar.progress =
                0

            scanningProgressText.text =
                "0%"
        }

        updateStatusFromBackground(
            "Found ${photos.size} photos. Reading videos..."
        )

        scanVideos(
            videos,
            selectedOnly
        )

        currentCoroutineContext().ensureActive()

        updateStatusFromBackground(
            "Found ${photos.size} photos and ${videos.size} videos. Checking duplicates..."
        )

        /*
         * First filter:
         *
         * Exact duplicates must have the same file size.
         *
         * Files with unique sizes never need hashing.
         */
        val photoCandidateIds =
            photos
                .groupBy {
                    it.size
                }
                .values
                .filter {
                    it.size > 1
                }
                .asSequence()
                .flatten()
                .map {
                    it.id
                }
                .toHashSet()

        val videoCandidateIds =
            videos
                .groupBy {
                    it.size
                }
                .values
                .filter {
                    it.size > 1
                }
                .asSequence()
                .flatten()
                .map {
                    it.id
                }
                .toHashSet()

        withContext(
            Dispatchers.Main
        ) {

            scanningStageText.text =
                "Quick-checking possible duplicate photos..."

            scanningProgressBar.progress =
                0

            scanningProgressText.text =
                "0%"
        }

        updateStatusFromBackground(
            "Quick-checking ${photoCandidateIds.size} possible duplicate photos..."
        )

        val photosWithHash =
            hashMediaFiles(
                files =
                    photos,

                candidateIds =
                    photoCandidateIds,

                mediaName =
                    "photos"
            )

        currentCoroutineContext().ensureActive()

        withContext(
            Dispatchers.Main
        ) {

            scanningStageText.text =
                "Quick-checking possible duplicate videos..."

            scanningProgressBar.progress =
                0

            scanningProgressText.text =
                "0%"
        }

        updateStatusFromBackground(
            "Quick-checking ${videoCandidateIds.size} possible duplicate videos..."
        )

        val videosWithHash =
            hashMediaFiles(
                files =
                    videos,

                candidateIds =
                    videoCandidateIds,

                mediaName =
                    "videos"
            )

        currentCoroutineContext().ensureActive()

        withContext(
            Dispatchers.Main
        ) {

            scanningStageText.text =
                "Grouping exact duplicates..."

            scanningProgressBar.progress =
                100

            scanningProgressText.text =
                "100%"
        }

        updateStatusFromBackground(
            "Grouping exact duplicates..."
        )

        val photoDuplicateGroups =
            findDuplicateGroups(
                photosWithHash
            )

        val videoDuplicateGroups =
            findDuplicateGroups(
                videosWithHash
            )

        val photoDuplicateCount =
            photoDuplicateGroups.sumOf {
                it.size - 1
            }

        val videoDuplicateCount =
            videoDuplicateGroups.sumOf {
                it.size - 1
            }

        val photoRecoverableBytes =
            photoDuplicateGroups.sumOf { group ->

                group
                    .drop(1)
                    .sumOf {
                        it.size
                    }
            }

        val videoRecoverableBytes =
            videoDuplicateGroups.sumOf { group ->

                group
                    .drop(1)
                    .sumOf {
                        it.size
                    }
            }

        return ScanResult(

            photos =
                photosWithHash,

            videos =
                videosWithHash,

            photoDuplicateGroups =
                photoDuplicateGroups,

            videoDuplicateGroups =
                videoDuplicateGroups,

            photoDuplicateCount =
                photoDuplicateCount,

            videoDuplicateCount =
                videoDuplicateCount,

            recoverableBytes =
                photoRecoverableBytes +
                        videoRecoverableBytes
        )
    }

    /*
     * Two-stage duplicate checking:
     *
     * 1. Quick fingerprint:
     *    Read only the first 128 KB.
     *
     * 2. Full SHA-256:
     *    Only files with the same size AND
     *    same quick fingerprint are fully hashed.
     *
     * This is a safe optimization:
     *
     * Different quick fingerprints mean the files
     * cannot be identical.
     *
     * A matching quick fingerprint does NOT mean
     * the files are duplicates, so full SHA-256
     * is still required.
     */
    private suspend fun hashMediaFiles(
        files: List<MediaFile>,
        candidateIds: Set<Long>,
        mediaName: String
    ): List<MediaFile> {

        if (
            candidateIds.isEmpty()
        ) {

            withContext(
                Dispatchers.Main
            ) {

                scanningProgressBar.progress =
                    100

                scanningProgressText.text =
                    "100%"
            }

            return files
        }

        /*
         * Quick fingerprint is calculated only for
         * files that already have the same size as
         * another file.
         */
        val quickFingerprintGroups =
            mutableMapOf<String, MutableList<MediaFile>>()

        var quickChecked =
            0

        val totalCandidates =
            candidateIds.size

        files.forEach { mediaFile ->

            currentCoroutineContext()
                .ensureActive()

            if (
                candidateIds.contains(
                    mediaFile.id
                )
            ) {

                val quickFingerprint =
                    calculateQuickFingerprint(
                        Uri.parse(
                            mediaFile.uri
                        )
                    )

                if (
                    quickFingerprint.isNotEmpty()
                ) {

                    val key =
                        "${mediaFile.size}:$quickFingerprint"

                    quickFingerprintGroups
                        .getOrPut(
                            key
                        ) {
                            mutableListOf()
                        }
                        .add(
                            mediaFile
                        )
                }

                quickChecked++

                if (
                    quickChecked == 1 ||
                    quickChecked % 10 == 0 ||
                    quickChecked == totalCandidates
                ) {

                    updateScanProgress(
                        stage =
                            "Quick-checking $mediaName",

                        current =
                            quickChecked,

                        total =
                            totalCandidates
                    )

                    updateStatusFromBackground(
                        "Quick-checking $mediaName: $quickChecked / $totalCandidates"
                    )
                }
            }
        }

        /*
         * Only quick-fingerprint groups containing
         * more than one file need full SHA-256.
         */
        val fullHashCandidateIds =
            quickFingerprintGroups
                .values
                .filter {
                    it.size > 1
                }
                .asSequence()
                .flatten()
                .map {
                    it.id
                }
                .toHashSet()

        val result =
            ArrayList<MediaFile>(
                files.size
            )

        var fullHashChecked =
            0

        val totalFullHashCandidates =
            fullHashCandidateIds.size

        files.forEach { mediaFile ->

            currentCoroutineContext()
                .ensureActive()

            if (
                fullHashCandidateIds.contains(
                    mediaFile.id
                )
            ) {

                val hash =
                    calculateSha256(
                        Uri.parse(
                            mediaFile.uri
                        )
                    )

                result.add(
                    mediaFile.copy(
                        sha256 =
                            hash
                    )
                )

                fullHashChecked++

                if (
                    fullHashChecked == 1 ||
                    fullHashChecked % 5 == 0 ||
                    fullHashChecked ==
                    totalFullHashCandidates
                ) {

                    updateScanProgress(
                        stage =
                            "Full-checking $mediaName",

                        current =
                            fullHashChecked,

                        total =
                            totalFullHashCandidates
                    )

                    updateStatusFromBackground(
                        "Full-checking $mediaName: $fullHashChecked / $totalFullHashCandidates"
                    )
                }

            } else {

                result.add(
                    mediaFile
                )
            }
        }

        withContext(
            Dispatchers.Main
        ) {

            scanningProgressBar.progress =
                100

            scanningProgressText.text =
                "100%"
        }

        return result
    }

    /*
     * Reads only the beginning of the file.
     *
     * This is NOT used as proof of duplication.
     * It is only a fast rejection test before
     * expensive full SHA-256 hashing.
     */
    private suspend fun calculateQuickFingerprint(
        uri: Uri
    ): String {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    BufferedInputStream(
                        inputStream
                    ).use { input ->

                        val buffer =
                            ByteArray(
                                QUICK_FINGERPRINT_SIZE
                            )

                        val bytesRead =
                            input.read(
                                buffer
                            )

                        if (
                            bytesRead > 0
                        ) {

                            digest.update(
                                buffer,
                                0,
                                bytesRead
                            )

                        } else {

                            return ""
                        }
                    }

                } ?: return ""

            digest
                .digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (
            cancellation: CancellationException
        ) {

            throw cancellation

        } catch (_: Exception) {

            ""
        }
    }

    private fun findDuplicateGroups(
        files: List<MediaFile>
    ): List<List<MediaFile>> {

        return files
            .asSequence()
            .filter {
                it.sha256.isNotEmpty()
            }
            .groupBy {
                it.sha256
            }
            .values
            .filter {
                it.size > 1
            }
            .map {
                it.toList()
            }
            .toList()
    }

    private suspend fun calculateSha256(
        uri: Uri
    ): String {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    BufferedInputStream(
                        inputStream
                    ).use { input ->

                        val buffer =
                            ByteArray(
                                1024 * 1024
                            )

                        while (true) {

                            currentCoroutineContext()
                                .ensureActive()

                            val bytesRead =
                                input.read(
                                    buffer
                                )

                            if (
                                bytesRead == -1
                            ) {

                                break
                            }

                            digest.update(
                                buffer,
                                0,
                                bytesRead
                            )
                        }
                    }

                } ?: return ""

            digest
                .digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (
            cancellation: CancellationException
        ) {

            throw cancellation

        } catch (_: Exception) {

            ""
        }
    }

    private suspend fun scanPhotos(
        photos: MutableList<MediaFile>,
        selectedOnly: Boolean
    ) {

        val projection =
            arrayOf(

                MediaStore.Images.Media._ID,

                MediaStore.Images.Media.DISPLAY_NAME,

                MediaStore.Images.Media.MIME_TYPE,

                MediaStore.Images.Media.SIZE,

                MediaStore.Images.Media.DATE_ADDED,

                MediaStore.Images.Media.DATE_MODIFIED,

                MediaStore.Images.Media.WIDTH,

                MediaStore.Images.Media.HEIGHT,

                MediaStore.Images.Media.RELATIVE_PATH
            )

        contentResolver.query(

            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,

            projection,

            null,
            null,
            null

        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media._ID
                )

            val nameColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DISPLAY_NAME
                )

            val mimeColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.MIME_TYPE
                )

            val sizeColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.SIZE
                )

            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_ADDED
                )

            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_MODIFIED
                )

            val widthColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.WIDTH
                )

            val heightColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.HEIGHT
                )

            val relativePathColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.RELATIVE_PATH
                )

            var scannedRows =
                0

            while (
                cursor.moveToNext()
            ) {

                currentCoroutineContext()
                    .ensureActive()

                scannedRows++

                val relativePath =
                    cursor.getString(
                        relativePathColumn
                    ) ?: ""

                if (
                    selectedOnly &&
                    !isPathSelected(
                        relativePath
                    )
                ) {

                    continue
                }

                val id =
                    cursor.getLong(
                        idColumn
                    )

                val uri =
                    ContentUris.withAppendedId(

                        MediaStore.Images.Media
                            .EXTERNAL_CONTENT_URI,

                        id
                    )

                photos.add(

                    MediaFile(

                        id =
                            id,

                        uri =
                            uri.toString(),

                        name =
                            cursor.getString(
                                nameColumn
                            ) ?: "",

                        mimeType =
                            cursor.getString(
                                mimeColumn
                            ) ?: "",

                        size =
                            cursor.getLong(
                                sizeColumn
                            ),

                        dateAdded =
                            cursor.getLong(
                                dateAddedColumn
                            ),

                        dateModified =
                            cursor.getLong(
                                dateModifiedColumn
                            ),

                        width =
                            cursor.getInt(
                                widthColumn
                            ),

                        height =
                            cursor.getInt(
                                heightColumn
                            ),

                        duration =
                            0L,

                        relativePath =
                            relativePath,

                        mediaType =
                            MediaType.PHOTO
                    )
                )

                if (
                    scannedRows % 500 == 0
                ) {

                    updateStatusFromBackground(
                        "Reading photos... ${photos.size} found"
                    )
                }
            }
        }
    }

    private suspend fun scanVideos(
        videos: MutableList<MediaFile>,
        selectedOnly: Boolean
    ) {

        val projection =
            arrayOf(

                MediaStore.Video.Media._ID,

                MediaStore.Video.Media.DISPLAY_NAME,

                MediaStore.Video.Media.MIME_TYPE,

                MediaStore.Video.Media.SIZE,

                MediaStore.Video.Media.DATE_ADDED,

                MediaStore.Video.Media.DATE_MODIFIED,

                MediaStore.Video.Media.WIDTH,

                MediaStore.Video.Media.HEIGHT,

                MediaStore.Video.Media.DURATION,

                MediaStore.Video.Media.RELATIVE_PATH
            )

        contentResolver.query(

            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,

            projection,

            null,
            null,
            null

        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media._ID
                )

            val nameColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DISPLAY_NAME
                )

            val mimeColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.MIME_TYPE
                )

            val sizeColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.SIZE
                )

            val dateAddedColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_ADDED
                )

            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_MODIFIED
                )

            val widthColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.WIDTH
                )

            val heightColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.HEIGHT
                )

            val durationColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DURATION
                )

            val relativePathColumn =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.RELATIVE_PATH
                )

            var scannedRows =
                0

            while (
                cursor.moveToNext()
            ) {

                currentCoroutineContext()
                    .ensureActive()

                scannedRows++

                val relativePath =
                    cursor.getString(
                        relativePathColumn
                    ) ?: ""

                if (
                    selectedOnly &&
                    !isPathSelected(
                        relativePath
                    )
                ) {

                    continue
                }

                val id =
                    cursor.getLong(
                        idColumn
                    )

                val uri =
                    ContentUris.withAppendedId(

                        MediaStore.Video.Media
                            .EXTERNAL_CONTENT_URI,

                        id
                    )

                videos.add(

                    MediaFile(

                        id =
                            id,

                        uri =
                            uri.toString(),

                        name =
                            cursor.getString(
                                nameColumn
                            ) ?: "",

                        mimeType =
                            cursor.getString(
                                mimeColumn
                            ) ?: "",

                        size =
                            cursor.getLong(
                                sizeColumn
                            ),

                        dateAdded =
                            cursor.getLong(
                                dateAddedColumn
                            ),

                        dateModified =
                            cursor.getLong(
                                dateModifiedColumn
                            ),

                        width =
                            cursor.getInt(
                                widthColumn
                            ),

                        height =
                            cursor.getInt(
                                heightColumn
                            ),

                        duration =
                            cursor.getLong(
                                durationColumn
                            ),

                        relativePath =
                            relativePath,

                        mediaType =
                            MediaType.VIDEO
                    )
                )

                if (
                    scannedRows % 500 == 0
                ) {

                    updateStatusFromBackground(
                        "Reading videos... ${videos.size} found"
                    )
                }
            }
        }
    }

    private fun isPathSelected(
        mediaStorePath: String
    ): Boolean {

        val normalizedMediaPath =
            normalizePath(
                mediaStorePath
            )

        return selectedFolderPaths.any {
                selectedPath ->

            val normalizedSelectedPath =
                normalizePath(
                    selectedPath
                )

            normalizedMediaPath ==
                    normalizedSelectedPath ||

                    normalizedMediaPath.startsWith(
                        "$normalizedSelectedPath/"
                    )
        }
    }

    private fun normalizePath(
        path: String
    ): String {

        return path
            .replace(
                "\\",
                "/"
            )
            .trim('/')
            .removePrefix(
                "primary:"
            )
    }

    private fun getFolderPathFromTreeUri(
        treeUri: Uri
    ): String {

        return try {

            val documentId =
                DocumentsContract
                    .getTreeDocumentId(
                        treeUri
                    )

            documentId
                .substringAfter(
                    ":",
                    documentId
                )
                .trim('/')

        } catch (_: Exception) {

            ""
        }
    }

    private fun buildResultMessage(
        result: ScanResult
    ): String {

        val totalDuplicates =
            result.photoDuplicateCount +
                    result.videoDuplicateCount

        if (
            totalDuplicates == 0
        ) {

            return getString(
                R.string.no_duplicates
            )
        }

        return getString(

            R.string.duplicates_found,

            totalDuplicates,

            formatBytes(
                result.recoverableBytes
            )
        )
    }

    private fun formatBytes(
        bytes: Long
    ): String {

        if (
            bytes < 1024
        ) {

            return "$bytes B"
        }

        if (
            bytes <
            1024L * 1024L
        ) {

            return String.format(
                Locale.US,
                "%.2f KB",
                bytes / 1024.0
            )
        }

        if (
            bytes <
            1024L *
            1024L *
            1024L
        ) {

            return String.format(
                Locale.US,
                "%.2f MB",
                bytes /
                        (
                                1024.0 *
                                        1024.0
                                )
            )
        }

        return String.format(
            Locale.US,
            "%.2f GB",
            bytes /
                    (
                            1024.0 *
                                    1024.0 *
                                    1024.0
                            )
        )
    }

    override fun onDestroy() {

        scanJob?.cancel()

        super.onDestroy()
    }

    data class ScanResult(

        val photos:
        List<MediaFile>,

        val videos:
        List<MediaFile>,

        val photoDuplicateGroups:
        List<List<MediaFile>>,

        val videoDuplicateGroups:
        List<List<MediaFile>>,

        val photoDuplicateCount:
        Int,

        val videoDuplicateCount:
        Int,

        val recoverableBytes:
        Long
    )

    data class MediaFile(

        val id:
        Long,

        val uri:
        String,

        val name:
        String,

        val mimeType:
        String,

        val size:
        Long,

        val dateAdded:
        Long,

        val dateModified:
        Long,

        val width:
        Int,

        val height:
        Int,

        val duration:
        Long,

        val relativePath:
        String,

        val mediaType:
        MediaType,

        val sha256:
        String = ""
    )

    enum class MediaType {
        PHOTO,
        VIDEO
    }

    companion object {

        /*
         * Number of bytes used for the fast
         * pre-check before full SHA-256.
         */
        private const val QUICK_FINGERPRINT_SIZE =
            128 * 1024
    }
}