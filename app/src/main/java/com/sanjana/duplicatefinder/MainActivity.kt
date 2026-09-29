package com.sanjana.duplicatefinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var titleText: android.widget.TextView
    private lateinit var subtitleText: android.widget.TextView

    private lateinit var scanAllButton:
            com.google.android.material.button.MaterialButton

    private lateinit var selectFoldersButton:
            com.google.android.material.button.MaterialButton

    private lateinit var scanSelectedButton:
            com.google.android.material.button.MaterialButton

    private lateinit var excludeFoldersButton:
            com.google.android.material.button.MaterialButton

    private lateinit var clearExcludedFoldersButton:
            com.google.android.material.button.MaterialButton

    private lateinit var scanningProgressContainer: View
    private lateinit var scanningStageText: android.widget.TextView
    private lateinit var scanningProgressText: android.widget.TextView
    private lateinit var scanningProgressBar: android.widget.ProgressBar

    private lateinit var cancelScanButton:
            com.google.android.material.button.MaterialButton

    private lateinit var progressBar:
            android.widget.ProgressBar

    private lateinit var statusText:
            android.widget.TextView

    private lateinit var selectedFoldersText:
            android.widget.TextView

    private lateinit var excludedFoldersText:
            android.widget.TextView

    private lateinit var photoCountText:
            android.widget.TextView

    private lateinit var videoCountText:
            android.widget.TextView

    private lateinit var totalCountText:
            android.widget.TextView

    private val selectedFolderUris =
        mutableListOf<Uri>()

    private val excludedFolderUris =
        mutableListOf<Uri>()

    private var scanJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val imageGranted =
                permissions[
                    Manifest.permission.READ_MEDIA_IMAGES
                ] == true

            val videoGranted =
                permissions[
                    Manifest.permission.READ_MEDIA_VIDEO
                ] == true

            val legacyGranted =
                permissions[
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ] == true

            if (
                imageGranted ||
                videoGranted ||
                legacyGranted
            ) {

                startScan(
                    scanAll = true
                )

            } else {

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
                // Some devices do not allow persistable permission here.
            }

            if (
                !selectedFolderUris.contains(uri)
            ) {

                selectedFolderUris.add(
                    uri
                )

                updateSelectedFoldersText()

                Toast.makeText(
                    this,
                    "Folder added.",
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
                // Some devices do not allow persistable permission here.
            }

            if (
                !excludedFolderUris.contains(uri)
            ) {

                excludedFolderUris.add(
                    uri
                )

                updateExcludedFoldersText()

                Toast.makeText(
                    this,
                    "Folder excluded.",
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

        scanAllButton.setOnClickListener {

            if (
                scanJob?.isActive == true
            ) {
                return@setOnClickListener
            }

            checkPermissionsAndScan(
                scanAll = true
            )
        }

        selectFoldersButton.setOnClickListener {

            if (
                scanJob?.isActive == true
            ) {
                return@setOnClickListener
            }

            folderPickerLauncher.launch(
                null
            )
        }

        scanSelectedButton.setOnClickListener {

            if (
                scanJob?.isActive == true
            ) {
                return@setOnClickListener
            }

            if (
                selectedFolderUris.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Select at least one folder first.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            checkPermissionsAndScan(
                scanAll = false
            )
        }

        excludeFoldersButton.setOnClickListener {

            if (
                scanJob?.isActive == true
            ) {
                return@setOnClickListener
            }

            excludeFolderPickerLauncher.launch(
                null
            )
        }

        clearExcludedFoldersButton.setOnClickListener {

            if (
                scanJob?.isActive == true
            ) {
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
                scanning = false
            )

            statusText.text =
                "Scan cancelled."

            Toast.makeText(
                this,
                "Scan cancelled.",
                Toast.LENGTH_SHORT
            ).show()
        }

        updateSelectedFoldersText()
        updateExcludedFoldersText()
    }

    private fun checkPermissionsAndScan(
        scanAll: Boolean
    ) {

        val permissions =
            requiredPermissions()

        val missingPermissions =
            permissions.filter { permission ->

                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (
            missingPermissions.isEmpty()
        ) {

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

        if (
            scanJob?.isActive == true
        ) {
            return
        }

        setScanningUi(
            scanning = true
        )

        statusText.text =
            "Scanning media..."

        scanJob =
            lifecycleScope.launch {

                try {

                    val result =
                        withContext(
                            Dispatchers.IO
                        ) {

                            scanMedia(
                                scanAll
                            )
                        }

                    currentCoroutineContext()
                        .ensureActive()

                    setScanningUi(
                        scanning = false
                    )

                    showScanResult(
                        result
                    )

                } catch (
                    exception: kotlinx.coroutines.CancellationException
                ) {

                    setScanningUi(
                        scanning = false
                    )

                    statusText.text =
                        "Scan cancelled."

                } catch (
                    exception: Exception
                ) {

                    setScanningUi(
                        scanning = false
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

    private suspend fun scanMedia(
        scanAll: Boolean
    ): ScanResult {

        updateProgress(
            "Reading media library...",
            5
        )

        val mediaFiles =
            queryMediaFiles(
                scanAll
            )

        currentCoroutineContext()
            .ensureActive()

        val photos =
            mediaFiles.filter {
                it.mediaType == MediaType.PHOTO
            }

        val videos =
            mediaFiles.filter {
                it.mediaType == MediaType.VIDEO
            }

        updateProgress(
            "Found ${photos.size} photos and ${videos.size} videos.",
            15
        )

        currentCoroutineContext()
            .ensureActive()

        val photoExactGroups =
            hashMediaFiles(
                photos,
                "Checking exact photo duplicates"
            )

        currentCoroutineContext()
            .ensureActive()

        val videoExactGroups =
            hashMediaFiles(
                videos,
                "Checking exact video duplicates"
            )

        currentCoroutineContext()
            .ensureActive()

        updateProgress(
            "Analyzing similar photos...",
            70
        )

        val exactPhotoUris =
            photoExactGroups
                .flatten()
                .map {
                    it.uri
                }
                .toSet()

        val similarPhotoSource =
            photos.filter {

                !exactPhotoUris.contains(
                    it.uri.toString()
                )
            }

        val similarPhotoGroups =
            findSimilarPhotoGroups(
                similarPhotoSource
            )

        currentCoroutineContext()
            .ensureActive()

        updateProgress(
            "Analyzing photo dates and times...",
            96
        )

        val exactPhotoUriSet =
            photoExactGroups
                .flatten()
                .map {
                    it.uri
                }
                .toSet()

        val dateTimePhotoSource =
            photos.filter { photo ->

                !exactPhotoUriSet.contains(
                    photo.uri.toString()
                )
            }

        val dateTimePhotoGroups =
            findDateTimePhotoGroups(
                dateTimePhotoSource
            )

        currentCoroutineContext()
            .ensureActive()

        updateProgress(
            "Finishing results...",
            100
        )

        return ScanResult(
            photos = photos,
            videos = videos,
            exactPhotoGroups = photoExactGroups,
            exactVideoGroups = videoExactGroups,
            similarPhotoGroups = similarPhotoGroups,
            dateTimePhotoGroups = dateTimePhotoGroups
        )
    }

    private suspend fun queryMediaFiles(
        scanAll: Boolean
    ): List<MediaFile> {

        val result =
            mutableListOf<MediaFile>()

        val imageCollection =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val imageProjection =
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
            imageCollection,
            imageProjection,
            null,
            null,
            null
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media._ID
                )

            val nameIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DISPLAY_NAME
                )

            val mimeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.MIME_TYPE
                )

            val sizeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.SIZE
                )

            val addedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_ADDED
                )

            val modifiedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.DATE_MODIFIED
                )

            val widthIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.WIDTH
                )

            val heightIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.HEIGHT
                )

            val pathIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Images.Media.RELATIVE_PATH
                )

            while (
                cursor.moveToNext()
            ) {

                currentCoroutineContext()
                    .ensureActive()

                val relativePath =
                    cursor.getString(
                        pathIndex
                    ) ?: ""

                if (
                    isExcludedPath(
                        relativePath
                    )
                ) {
                    continue
                }

                if (
                    !scanAll &&
                    !isInsideSelectedFolder(
                        relativePath
                    )
                ) {
                    continue
                }

                val id =
                    cursor.getLong(
                        idIndex
                    )

                val uri =
                    Uri.withAppendedPath(
                        imageCollection,
                        id.toString()
                    )

                result.add(
                    MediaFile(
                        uri = uri,
                        name =
                            cursor.getString(
                                nameIndex
                            ) ?: "Unknown",
                        mimeType =
                            cursor.getString(
                                mimeIndex
                            ) ?: "",
                        size =
                            cursor.getLong(
                                sizeIndex
                            ),
                        dateAdded =
                            cursor.getLong(
                                addedIndex
                            ),
                        dateModified =
                            cursor.getLong(
                                modifiedIndex
                            ),
                        width =
                            cursor.getInt(
                                widthIndex
                            ),
                        height =
                            cursor.getInt(
                                heightIndex
                            ),
                        duration = 0L,
                        relativePath =
                            relativePath,
                        mediaType =
                            MediaType.PHOTO
                    )
                )
            }
        }

        updateProgress(
            "Reading videos...",
            10
        )

        val videoCollection =
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val videoProjection =
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
            videoCollection,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media._ID
                )

            val nameIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DISPLAY_NAME
                )

            val mimeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.MIME_TYPE
                )

            val sizeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.SIZE
                )

            val addedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_ADDED
                )

            val modifiedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DATE_MODIFIED
                )

            val widthIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.WIDTH
                )

            val heightIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.HEIGHT
                )

            val durationIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DURATION
                )

            val pathIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.RELATIVE_PATH
                )

            while (
                cursor.moveToNext()
            ) {

                currentCoroutineContext()
                    .ensureActive()

                val relativePath =
                    cursor.getString(
                        pathIndex
                    ) ?: ""

                if (
                    isExcludedPath(
                        relativePath
                    )
                ) {
                    continue
                }

                if (
                    !scanAll &&
                    !isInsideSelectedFolder(
                        relativePath
                    )
                ) {
                    continue
                }

                val id =
                    cursor.getLong(
                        idIndex
                    )

                val uri =
                    Uri.withAppendedPath(
                        videoCollection,
                        id.toString()
                    )

                result.add(
                    MediaFile(
                        uri = uri,
                        name =
                            cursor.getString(
                                nameIndex
                            ) ?: "Unknown",
                        mimeType =
                            cursor.getString(
                                mimeIndex
                            ) ?: "",
                        size =
                            cursor.getLong(
                                sizeIndex
                            ),
                        dateAdded =
                            cursor.getLong(
                                addedIndex
                            ),
                        dateModified =
                            cursor.getLong(
                                modifiedIndex
                            ),
                        width =
                            cursor.getInt(
                                widthIndex
                            ),
                        height =
                            cursor.getInt(
                                heightIndex
                            ),
                        duration =
                            cursor.getLong(
                                durationIndex
                            ),
                        relativePath =
                            relativePath,
                        mediaType =
                            MediaType.VIDEO
                    )
                )
            }
        }

        return result
    }

    private fun isInsideSelectedFolder(
        relativePath: String
    ): Boolean {

        if (
            selectedFolderUris.isEmpty()
        ) {
            return false
        }

        val normalizedMediaPath =
            relativePath
                .trim('/')
                .lowercase(Locale.US)

        return selectedFolderUris.any { treeUri ->

            val treePath =
                getTreeRelativePath(
                    treeUri
                )
                    .trim('/')
                    .lowercase(Locale.US)

            if (
                treePath.isBlank()
            ) {

                true

            } else {

                normalizedMediaPath == treePath ||
                        normalizedMediaPath.startsWith(
                            "$treePath/"
                        )
            }
        }
    }

    private fun isExcludedPath(
        relativePath: String
    ): Boolean {

        if (
            excludedFolderUris.isEmpty()
        ) {
            return false
        }

        val normalizedMediaPath =
            relativePath
                .trim('/')
                .lowercase(Locale.US)

        return excludedFolderUris.any { treeUri ->

            val treePath =
                getTreeRelativePath(
                    treeUri
                )
                    .trim('/')
                    .lowercase(Locale.US)

            if (
                treePath.isBlank()
            ) {

                false

            } else {

                normalizedMediaPath == treePath ||
                        normalizedMediaPath.startsWith(
                            "$treePath/"
                        )
            }
        }
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

    private suspend fun hashMediaFiles(
        files: List<MediaFile>,
        stage: String
    ): List<List<DuplicateResultsActivity.DuplicateItem>> {

        if (
            files.isEmpty()
        ) {
            return emptyList()
        }

        val sizeGroups =
            files
                .groupBy {
                    it.size
                }
                .values
                .filter {
                    it.size > 1
                }

        if (
            sizeGroups.isEmpty()
        ) {

            updateProgress(
                stage,
                55
            )

            return emptyList()
        }

        val quickFingerprintGroups =
            mutableMapOf<
                    String,
                    MutableList<MediaFile>
                    >()

        var processed =
            0

        val totalCandidates =
            sizeGroups.sumOf {
                it.size
            }

        for (
        sizeGroup in sizeGroups
        ) {

            for (
            file in sizeGroup
            ) {

                currentCoroutineContext()
                    .ensureActive()

                val quick =
                    calculateQuickFingerprint(
                        file.uri
                    )

                if (
                    quick != null
                ) {

                    val key =
                        "${file.size}:$quick"

                    quickFingerprintGroups
                        .getOrPut(
                            key
                        ) {
                            mutableListOf()
                        }
                        .add(
                            file
                        )
                }

                processed++

                if (
                    processed % 20 == 0 ||
                    processed == totalCandidates
                ) {

                    val progress =
                        20 +
                                (
                                        processed.toDouble() /
                                                totalCandidates
                                                    .coerceAtLeast(1) *
                                                35
                                        ).toInt()

                    updateProgress(
                        stage,
                        progress.coerceAtMost(
                            55
                        )
                    )
                }
            }
        }

        val fullCandidates =
            quickFingerprintGroups
                .values
                .filter {
                    it.size > 1
                }
                .flatten()

        if (
            fullCandidates.isEmpty()
        ) {
            return emptyList()
        }

        val hashGroups =
            mutableMapOf<
                    String,
                    MutableList<
                            DuplicateResultsActivity.DuplicateItem
                            >
                    >()

        var hashed =
            0

        for (
        file in fullCandidates
        ) {

            currentCoroutineContext()
                .ensureActive()

            val sha256 =
                calculateSha256(
                    file.uri
                )

            if (
                sha256 != null
            ) {

                val item =
                    file.toDuplicateItem(
                        sha256
                    )

                hashGroups
                    .getOrPut(
                        sha256
                    ) {
                        mutableListOf()
                    }
                    .add(
                        item
                    )
            }

            hashed++

            if (
                hashed % 5 == 0 ||
                hashed == fullCandidates.size
            ) {

                val progress =
                    55 +
                            (
                                    hashed.toDouble() /
                                            fullCandidates.size
                                                .coerceAtLeast(1) *
                                            15
                                    ).toInt()

                updateProgress(
                    stage,
                    progress.coerceAtMost(
                        70
                    )
                )
            }
        }

        return hashGroups
            .values
            .filter {
                it.size > 1
            }
            .map {
                it.toList()
            }
    }

    private suspend fun findSimilarPhotoGroups(
        photos: List<MediaFile>
    ): List<List<DuplicateResultsActivity.DuplicateItem>> {

        if (
            photos.size < 2
        ) {
            return emptyList()
        }

        val hashedPhotos =
            mutableListOf<PhotoHash>()

        var processed =
            0

        for (
        photo in photos
        ) {

            currentCoroutineContext()
                .ensureActive()

            val hash =
                calculatePerceptualHash(
                    photo.uri
                )

            if (
                hash != null
            ) {

                hashedPhotos.add(
                    PhotoHash(
                        photo = photo,
                        hash = hash
                    )
                )
            }

            processed++

            if (
                processed % 5 == 0 ||
                processed == photos.size
            ) {

                val progress =
                    70 +
                            (
                                    processed.toDouble() /
                                            photos.size
                                                .coerceAtLeast(1) *
                                            25
                                    ).toInt()

                updateProgress(
                    "Analyzing similar photos...",
                    progress.coerceAtMost(
                        95
                    )
                )
            }
        }

        val buckets =
            mutableMapOf<
                    Int,
                    MutableList<PhotoHash>
                    >()

        hashedPhotos.forEach { item ->

            val bucket =
                (item.hash ushr 56)
                    .toInt()

            buckets
                .getOrPut(
                    bucket
                ) {
                    mutableListOf()
                }
                .add(
                    item
                )
        }

        val adjacency =
            mutableMapOf<
                    String,
                    MutableSet<String>
                    >()

        for (
        bucketItems in buckets.values
        ) {

            for (
            i in bucketItems.indices
            ) {

                for (
                j in i + 1 until bucketItems.size
                ) {

                    currentCoroutineContext()
                        .ensureActive()

                    val first =
                        bucketItems[i]

                    val second =
                        bucketItems[j]

                    val distance =
                        hammingDistance(
                            first.hash,
                            second.hash
                        )

                    if (
                        distance <=
                        SIMILAR_PHOTO_THRESHOLD
                    ) {

                        val firstUri =
                            first.photo.uri.toString()

                        val secondUri =
                            second.photo.uri.toString()

                        adjacency
                            .getOrPut(
                                firstUri
                            ) {
                                mutableSetOf()
                            }
                            .add(
                                secondUri
                            )

                        adjacency
                            .getOrPut(
                                secondUri
                            ) {
                                mutableSetOf()
                            }
                            .add(
                                firstUri
                            )
                    }
                }
            }
        }

        val photoByUri =
            hashedPhotos.associateBy {
                it.photo.uri.toString()
            }

        val visited =
            mutableSetOf<String>()

        val groups =
            mutableListOf<
                    List<
                            DuplicateResultsActivity.DuplicateItem
                            >
                    >()

        for (
        item in hashedPhotos
        ) {

            currentCoroutineContext()
                .ensureActive()

            val startUri =
                item.photo.uri.toString()

            if (
                visited.contains(
                    startUri
                )
            ) {
                continue
            }

            val neighbors =
                adjacency[startUri]

            if (
                neighbors.isNullOrEmpty()
            ) {
                continue
            }

            val queue =
                ArrayDeque<String>()

            val groupUris =
                mutableListOf<String>()

            queue.add(
                startUri
            )

            visited.add(
                startUri
            )

            while (
                queue.isNotEmpty()
            ) {

                currentCoroutineContext()
                    .ensureActive()

                val current =
                    queue.removeFirst()

                groupUris.add(
                    current
                )

                adjacency[current]
                    ?.forEach { next ->

                        if (
                            visited.add(
                                next
                            )
                        ) {

                            queue.add(
                                next
                            )
                        }
                    }
            }

            if (
                groupUris.size > 1
            ) {

                val group =
                    groupUris.mapNotNull { uri ->

                        photoByUri[uri]
                            ?.photo
                            ?.toDuplicateItem(
                                sha256 = ""
                            )
                    }

                if (
                    group.size > 1
                ) {

                    groups.add(
                        group
                    )
                }
            }
        }

        return groups
    }

    private suspend fun findDateTimePhotoGroups(
        photos: List<MediaFile>
    ): List<List<DuplicateResultsActivity.DuplicateItem>> {

        if (
            photos.size < 2
        ) {
            return emptyList()
        }

        val sortedPhotos =
            photos
                .filter {
                    it.dateAdded > 0
                }
                .sortedBy {
                    it.dateAdded
                }

        if (
            sortedPhotos.size < 2
        ) {
            return emptyList()
        }

        val groups =
            mutableListOf<
                    List<
                            DuplicateResultsActivity.DuplicateItem
                            >
                    >()

        var currentGroup =
            mutableListOf<MediaFile>()

        for (
        index in sortedPhotos.indices
        ) {

            currentCoroutineContext()
                .ensureActive()

            val current =
                sortedPhotos[index]

            if (
                currentGroup.isEmpty()
            ) {

                currentGroup.add(
                    current
                )

                continue
            }

            val previous =
                currentGroup.last()

            val difference =
                current.dateAdded -
                        previous.dateAdded

            if (
                difference <=
                DATE_TIME_WINDOW_SECONDS
            ) {

                currentGroup.add(
                    current
                )

            } else {

                if (
                    currentGroup.size >= 2
                ) {

                    groups.add(
                        currentGroup.map {
                            it.toDuplicateItem(
                                sha256 = ""
                            )
                        }
                    )
                }

                currentGroup =
                    mutableListOf()

                currentGroup.add(
                    current
                )
            }
        }

        if (
            currentGroup.size >= 2
        ) {

            groups.add(
                currentGroup.map {
                    it.toDuplicateItem(
                        sha256 = ""
                    )
                }
            )
        }

        return groups
    }

    private fun calculatePerceptualHash(
        uri: Uri
    ): Long? {

        return try {

            val bitmap =
                contentResolver
                    .openInputStream(uri)
                    ?.use { input ->

                        BitmapFactory
                            .decodeStream(
                                input
                            )
                    }
                    ?: return null

            val scaled =
                Bitmap.createScaledBitmap(
                    bitmap,
                    PHASH_SIZE,
                    PHASH_SIZE,
                    true
                )

            val gray =
                DoubleArray(
                    PHASH_SIZE *
                            PHASH_SIZE
                )

            for (
            y in 0 until PHASH_SIZE
            ) {

                for (
                x in 0 until PHASH_SIZE
                ) {

                    val pixel =
                        scaled.getPixel(
                            x,
                            y
                        )

                    val red =
                        (pixel shr 16) and 0xff

                    val green =
                        (pixel shr 8) and 0xff

                    val blue =
                        pixel and 0xff

                    gray[
                        y *
                                PHASH_SIZE +
                                x
                    ] =
                        0.299 * red +
                                0.587 * green +
                                0.114 * blue
                }
            }

            if (
                scaled !== bitmap
            ) {
                scaled.recycle()
            }

            if (
                !bitmap.isRecycled
            ) {
                bitmap.recycle()
            }

            val dct =
                Array(
                    DCT_SIZE
                ) {
                    DoubleArray(
                        DCT_SIZE
                    )
                }

            for (
            u in 0 until DCT_SIZE
            ) {

                for (
                v in 0 until DCT_SIZE
                ) {

                    var sum =
                        0.0

                    for (
                    x in 0 until PHASH_SIZE
                    ) {

                        for (
                        y in 0 until PHASH_SIZE
                        ) {

                            sum +=
                                gray[
                                    y *
                                            PHASH_SIZE +
                                            x
                                ] *
                                        kotlin.math.cos(
                                            (
                                                    2.0 *
                                                            x +
                                                            1.0
                                                    ) *
                                                    u *
                                                    Math.PI /
                                                    (
                                                            2.0 *
                                                                    PHASH_SIZE
                                                            )
                                        ) *
                                        kotlin.math.cos(
                                            (
                                                    2.0 *
                                                            y +
                                                            1.0
                                                    ) *
                                                    v *
                                                    Math.PI /
                                                    (
                                                            2.0 *
                                                                    PHASH_SIZE
                                                            )
                                        )
                        }
                    }

                    val alphaU =
                        if (
                            u == 0
                        ) {

                            1.0 /
                                    kotlin.math.sqrt(
                                        PHASH_SIZE.toDouble()
                                    )

                        } else {

                            kotlin.math.sqrt(
                                2.0 /
                                        PHASH_SIZE
                            )
                        }

                    val alphaV =
                        if (
                            v == 0
                        ) {

                            1.0 /
                                    kotlin.math.sqrt(
                                        PHASH_SIZE.toDouble()
                                    )

                        } else {

                            kotlin.math.sqrt(
                                2.0 /
                                        PHASH_SIZE
                            )
                        }

                    dct[u][v] =
                        alphaU *
                                alphaV *
                                sum
                }
            }

            val values =
                mutableListOf<Double>()

            for (
            u in 0 until DCT_SIZE
            ) {

                for (
                v in 0 until DCT_SIZE
                ) {

                    if (
                        u == 0 &&
                        v == 0
                    ) {
                        continue
                    }

                    values.add(
                        dct[u][v]
                    )
                }
            }

            val sorted =
                values.sorted()

            val median =
                sorted[
                    sorted.size / 2
                ]

            var hash =
                0L

            var bit =
                0

            for (
            u in 0 until DCT_SIZE
            ) {

                for (
                v in 0 until DCT_SIZE
                ) {

                    if (
                        u == 0 &&
                        v == 0
                    ) {
                        continue
                    }

                    if (
                        dct[u][v] >
                        median
                    ) {

                        hash =
                            hash or
                                    (
                                            1L shl bit
                                            )
                    }

                    bit++

                    if (
                        bit >= 63
                    ) {

                        return hash
                    }
                }
            }

            hash

        } catch (_: Exception) {

            null
        }
    }

    private fun hammingDistance(
        first: Long,
        second: Long
    ): Int {

        return java.lang.Long
            .bitCount(
                first xor second
            )
    }

    private fun calculateQuickFingerprint(
        uri: Uri
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val buffer =
                        ByteArray(
                            QUICK_FINGERPRINT_SIZE
                        )

                    var remaining =
                        QUICK_FINGERPRINT_SIZE

                    while (
                        remaining > 0
                    ) {

                        val read =
                            input.read(
                                buffer,
                                QUICK_FINGERPRINT_SIZE -
                                        remaining,
                                remaining
                            )

                        if (
                            read <= 0
                        ) {
                            break
                        }

                        digest.update(
                            buffer,
                            QUICK_FINGERPRINT_SIZE -
                                    remaining,
                            read
                        )

                        remaining -= read
                    }
                }
                ?: return null

            digest
                .digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun calculateSha256(
        uri: Uri
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val buffer =
                        ByteArray(
                            64 * 1024
                        )

                    while (true) {

                        val read =
                            input.read(
                                buffer
                            )

                        if (
                            read <= 0
                        ) {
                            break
                        }

                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }
                ?: return null

            digest
                .digest()
                .joinToString("") {
                    "%02x".format(it)
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun updateProgress(
        stage: String,
        progress: Int
    ) {

        runOnUiThread {

            scanningStageText.text =
                stage

            scanningProgressBar.progress =
                progress.coerceIn(
                    0,
                    100
                )

            scanningProgressText.text =
                "${progress.coerceIn(0, 100)}%"
        }
    }

    private fun setScanningUi(
        scanning: Boolean
    ) {

        scanningProgressContainer.visibility =
            if (
                scanning
            ) {

                View.VISIBLE

            } else {

                View.GONE
            }

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

        if (
            selectedFolderUris.isEmpty()
        ) {

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

        if (
            excludedFolderUris.isEmpty()
        ) {

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

    private fun showScanResult(
        result: ScanResult
    ) {

        photoCountText.text =
            "Photos: ${result.photos.size}"

        videoCountText.text =
            "Videos: ${result.videos.size}"

        totalCountText.text =
            "Total: ${
                result.photos.size +
                        result.videos.size
            }"

        val exactDuplicateCount =
            result.exactPhotoGroups.sumOf {
                maxOf(
                    0,
                    it.size - 1
                )
            } +
                    result.exactVideoGroups.sumOf {
                        maxOf(
                            0,
                            it.size - 1
                        )
                    }

        val similarCount =
            result.similarPhotoGroups.size

        val dateTimeCount =
            result.dateTimePhotoGroups.size

        statusText.text =
            buildString {

                append(
                    "Scan complete. "
                )

                append(
                    exactDuplicateCount
                )

                append(
                    " exact duplicate files"
                )

                if (
                    similarCount > 0
                ) {

                    append(
                        " • "
                    )

                    append(
                        similarCount
                    )

                    append(
                        " similar photo groups"
                    )
                }

                if (
                    dateTimeCount > 0
                ) {

                    append(
                        " • "
                    )

                    append(
                        dateTimeCount
                    )

                    append(
                        " date/time groups"
                    )
                }
            }

        if (
            result.exactPhotoGroups.isEmpty() &&
            result.exactVideoGroups.isEmpty() &&
            result.similarPhotoGroups.isEmpty() &&
            result.dateTimePhotoGroups.isEmpty()
        ) {

            Toast.makeText(
                this,
                "No duplicates, similar photos, or date/time groups found.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val intent =
            Intent(
                this,
                DuplicateResultsActivity::class.java
            )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_PHOTO_GROUPS,
            ArrayList(
                result.exactPhotoGroups.map {
                    ArrayList(it)
                }
            )
        )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_VIDEO_GROUPS,
            ArrayList(
                result.exactVideoGroups.map {
                    ArrayList(it)
                }
            )
        )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_SIMILAR_PHOTO_GROUPS,
            ArrayList(
                result.similarPhotoGroups.map {
                    ArrayList(it)
                }
            )
        )

        intent.putExtra(
            DuplicateResultsActivity.EXTRA_DATE_TIME_PHOTO_GROUPS,
            ArrayList(
                result.dateTimePhotoGroups.map {
                    ArrayList(it)
                }
            )
        )

        startActivity(
            intent
        )
    }

    private fun MediaFile.toDuplicateItem(
        sha256: String
    ): DuplicateResultsActivity.DuplicateItem {

        return DuplicateResultsActivity.DuplicateItem(
            name = name,
            relativePath = relativePath,
            size = size,
            width = width,
            height = height,
            sha256 = sha256,
            uri = uri.toString()
        )
    }

    private data class MediaFile(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val dateAdded: Long,
        val dateModified: Long,
        val width: Int,
        val height: Int,
        val duration: Long,
        val relativePath: String,
        val mediaType: MediaType
    )

    private data class PhotoHash(
        val photo: MediaFile,
        val hash: Long
    )

    private data class ScanResult(
        val photos: List<MediaFile>,
        val videos: List<MediaFile>,
        val exactPhotoGroups:
        List<List<DuplicateResultsActivity.DuplicateItem>>,
        val exactVideoGroups:
        List<List<DuplicateResultsActivity.DuplicateItem>>,
        val similarPhotoGroups:
        List<List<DuplicateResultsActivity.DuplicateItem>>,
        val dateTimePhotoGroups:
        List<List<DuplicateResultsActivity.DuplicateItem>>
    )

    private enum class MediaType {
        PHOTO,
        VIDEO
    }

    companion object {

        private const val QUICK_FINGERPRINT_SIZE =
            128 * 1024

        private const val PHASH_SIZE =
            32

        private const val DCT_SIZE =
            8

        private const val SIMILAR_PHOTO_THRESHOLD =
            8

        private const val DATE_TIME_WINDOW_SECONDS =
            5 * 60L
    }
}

