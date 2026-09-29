package com.sanjana.duplicatefinder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sanjana.duplicatefinder.database.AppDatabase
import com.sanjana.duplicatefinder.database.MediaDao
import com.sanjana.duplicatefinder.database.MediaEntity
import java.security.MessageDigest
import java.util.Locale

object ScanManager {

    private const val QUICK_FINGERPRINT_SIZE = 64 * 1024

    private const val FRAME_SAMPLE_COUNT = 8
    private const val FRAME_WIDTH = 16
    private const val FRAME_HEIGHT = 16

    // Phase 4.3
    private const val SIMILARITY_THRESHOLD = 0.85

    fun scan(
        context: Context,
        selectedRelativePaths: List<String>,
        excludedRelativePaths: List<String>,
        scanAll: Boolean,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ) {

        val resolver = context.contentResolver
        val database = AppDatabase.getInstance(context)
        val dao = database.mediaDao()

        onProgress("Preparing media scan...")

        val selectedPaths = selectedRelativePaths
            .map { normalizePath(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val excludedPaths = excludedRelativePaths
            .map { normalizePath(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val mediaEntities = ArrayList<MediaEntity>()

        scanMediaStore(
            resolver,
            "PHOTO",
            selectedPaths,
            excludedPaths,
            scanAll,
            mediaEntities,
            onProgress,
            isCancelled
        )

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        scanMediaStore(
            resolver,
            "VIDEO",
            selectedPaths,
            excludedPaths,
            scanAll,
            mediaEntities,
            onProgress,
            isCancelled
        )

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        onProgress(
            "Saving ${mediaEntities.size} media files..."
        )

        dao.deleteAll()

        if (mediaEntities.isNotEmpty()) {
            dao.insertAll(mediaEntities)
        }

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        // =========================================================
        // PHASE 4.2
        // Generate visual fingerprints for indexed videos.
        // =========================================================

        generateVideoFingerprints(
            context,
            dao,
            onProgress,
            isCancelled
        )

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        // =========================================================
        // PHASE 4.3
        // Compare video fingerprints.
        // =========================================================

        findSimilarVideos(
            dao,
            onProgress,
            isCancelled
        )

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        onProgress("Finding exact duplicates...")

        findExactDuplicates(
            context,
            dao,
            onProgress,
            isCancelled
        )

        if (isCancelled()) {
            onProgress("Scan cancelled.")
            return
        }

        onProgress("Scan completed.")
    }

    // =============================================================
    // MEDIASTORE SCANNING
    // =============================================================

    private fun scanMediaStore(
        resolver: ContentResolver,
        mediaType: String,
        selectedPaths: List<String>,
        excludedPaths: List<String>,
        scanAll: Boolean,
        output: MutableList<MediaEntity>,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ) {

        val collection =
            if (mediaType == "VIDEO") {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }

        if (mediaType == "VIDEO") {
            projection.add(MediaStore.Video.VideoColumns.WIDTH)
            projection.add(MediaStore.Video.VideoColumns.HEIGHT)
            projection.add(MediaStore.Video.VideoColumns.DURATION)
        } else {
            projection.add(MediaStore.Images.ImageColumns.WIDTH)
            projection.add(MediaStore.Images.ImageColumns.HEIGHT)
        }

        resolver.query(
            collection,
            projection.toTypedArray(),
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns._ID
                )

            val nameIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DISPLAY_NAME
                )

            val mimeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.MIME_TYPE
                )

            val sizeIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.SIZE
                )

            val addedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DATE_ADDED
                )

            val modifiedIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns.DATE_MODIFIED
                )

            val relativePathIndex =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(
                        MediaStore.MediaColumns.RELATIVE_PATH
                    )
                } else {
                    -1
                }

            val widthIndex =
                if (mediaType == "VIDEO") {
                    cursor.getColumnIndex(
                        MediaStore.Video.VideoColumns.WIDTH
                    )
                } else {
                    cursor.getColumnIndex(
                        MediaStore.Images.ImageColumns.WIDTH
                    )
                }

            val heightIndex =
                if (mediaType == "VIDEO") {
                    cursor.getColumnIndex(
                        MediaStore.Video.VideoColumns.HEIGHT
                    )
                } else {
                    cursor.getColumnIndex(
                        MediaStore.Images.ImageColumns.HEIGHT
                    )
                }

            val durationIndex =
                if (mediaType == "VIDEO") {
                    cursor.getColumnIndex(
                        MediaStore.Video.VideoColumns.DURATION
                    )
                } else {
                    -1
                }

            var scannedCount = 0

            while (cursor.moveToNext()) {

                if (isCancelled()) {
                    return
                }

                val id = cursor.getLong(idIndex)

                val name =
                    cursor.getString(nameIndex) ?: ""

                val mimeType =
                    cursor.getString(mimeIndex) ?: ""

                val size =
                    cursor.getLong(sizeIndex)

                val dateAdded =
                    cursor.getLong(addedIndex)

                val dateModified =
                    cursor.getLong(modifiedIndex)

                val relativePath =
                    if (relativePathIndex >= 0) {
                        cursor.getString(relativePathIndex) ?: ""
                    } else {
                        ""
                    }

                val width =
                    if (widthIndex >= 0 && !cursor.isNull(widthIndex)) {
                        cursor.getInt(widthIndex)
                    } else {
                        0
                    }

                val height =
                    if (heightIndex >= 0 && !cursor.isNull(heightIndex)) {
                        cursor.getInt(heightIndex)
                    } else {
                        0
                    }

                val duration =
                    if (durationIndex >= 0 && !cursor.isNull(durationIndex)) {
                        cursor.getLong(durationIndex)
                    } else {
                        0L
                    }

                val uri = Uri.withAppendedPath(
                    collection,
                    id.toString()
                )

                if (
                    shouldSkipPath(
                        relativePath,
                        selectedPaths,
                        excludedPaths,
                        scanAll
                    )
                ) {
                    continue
                }

                // MediaEntity is a Java class.
                // Use positional constructor arguments.
                val entity = MediaEntity(
                    uri.toString(),
                    name,
                    mimeType,
                    size,
                    dateAdded,
                    dateModified,
                    width,
                    height,
                    duration,
                    relativePath,
                    mediaType,
                    System.currentTimeMillis()
                )

                if (size > 0) {
                    entity.quickFingerprint =
                        createQuickFingerprint(
                            resolver,
                            uri,
                            size
                        )
                }

                output.add(entity)

                scannedCount++

                if (scannedCount % 100 == 0) {
                    onProgress(
                        "Scanning $mediaType files... $scannedCount"
                    )
                }
            }
        }
    }

    // =============================================================
    // VIDEO FRAME FINGERPRINTING
    // PHASE 4.2
    // =============================================================

    private fun generateVideoFingerprints(
        context: Context,
        dao: MediaDao,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ) {

        val videos: List<MediaEntity> =
            dao.getAllVideos()

        if (videos.isEmpty()) {
            onProgress("No videos found.")
            return
        }

        val total = videos.size

        onProgress(
            "Analyzing video frames... 0 / $total"
        )

        var processed = 0

        for (video in videos) {

            if (isCancelled()) {
                return
            }

            try {

                val fingerprint =
                    createVideoVisualFingerprint(
                        context,
                        video,
                        isCancelled
                    )

                if (!fingerprint.isNullOrEmpty()) {
                    dao.updateVideoFingerprint(
                        video.uri,
                        fingerprint
                    )
                }

            } catch (_: Exception) {
                // Continue with remaining videos.
            }

            processed++

            onProgress(
                "Analyzing video frames... " +
                        "$processed / $total"
            )
        }
    }

    private fun createVideoVisualFingerprint(
        context: Context,
        video: MediaEntity,
        isCancelled: () -> Boolean
    ): String? {

        val uri = Uri.parse(video.uri)

        val retriever =
            MediaMetadataRetriever()

        try {

            val parcelFileDescriptor =
                context.contentResolver.openFileDescriptor(
                    uri,
                    "r"
                ) ?: return null

            parcelFileDescriptor.use { descriptor ->

                retriever.setDataSource(
                    descriptor.fileDescriptor
                )

                val durationMs =
                    if (video.duration > 0L) {
                        video.duration
                    } else {
                        retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                    }

                if (durationMs <= 0L) {
                    return null
                }

                val result = ByteArray(
                    FRAME_SAMPLE_COUNT *
                            FRAME_WIDTH *
                            FRAME_HEIGHT
                )

                var resultOffset = 0

                for (
                frameIndex
                in 0 until FRAME_SAMPLE_COUNT
                ) {

                    if (isCancelled()) {
                        return null
                    }

                    val fraction =
                        if (FRAME_SAMPLE_COUNT == 1) {
                            0.5
                        } else {
                            0.08 +
                                    (
                                            0.84 *
                                                    frameIndex /
                                                    (FRAME_SAMPLE_COUNT - 1)
                                            )
                        }

                    val positionUs =
                        (
                                durationMs *
                                        1000.0 *
                                        fraction
                                ).toLong()

                    val bitmap =
                        retriever.getFrameAtTime(
                            positionUs,
                            MediaMetadataRetriever.OPTION_CLOSEST
                        ) ?: return null

                    try {

                        val smallBitmap =
                            Bitmap.createScaledBitmap(
                                bitmap,
                                FRAME_WIDTH,
                                FRAME_HEIGHT,
                                true
                            )

                        try {

                            for (y in 0 until FRAME_HEIGHT) {

                                for (x in 0 until FRAME_WIDTH) {

                                    val pixel =
                                        smallBitmap.getPixel(
                                            x,
                                            y
                                        )

                                    val red =
                                        (pixel shr 16) and 0xFF

                                    val green =
                                        (pixel shr 8) and 0xFF

                                    val blue =
                                        pixel and 0xFF

                                    val gray =
                                        (
                                                0.299 * red +
                                                        0.587 * green +
                                                        0.114 * blue
                                                ).toInt()

                                    val quantized =
                                        (gray / 16)
                                            .coerceIn(0, 15)

                                    result[resultOffset++] =
                                        quantized.toByte()
                                }
                            }

                        } finally {

                            if (smallBitmap !== bitmap) {
                                smallBitmap.recycle()
                            }
                        }

                    } finally {
                        bitmap.recycle()
                    }
                }

                return bytesToHex(result)
            }

        } finally {

            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    // =============================================================
    // SIMILAR VIDEO DETECTION
    // PHASE 4.3
    // =============================================================

    private fun findSimilarVideos(
        dao: MediaDao,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ) {

        val videos =
            dao.getVideosWithFingerprints()

        if (videos.size < 2) {
            onProgress(
                "Not enough videos for similarity analysis."
            )
            return
        }

        val totalComparisons =
            videos.size.toLong() *
                    (videos.size - 1L) /
                    2L

        onProgress(
            "Comparing similar videos... 0 / $totalComparisons"
        )

        var comparisonCount = 0L
        var similarPairCount = 0

        for (i in 0 until videos.size - 1) {

            if (isCancelled()) {
                return
            }

            val firstVideo = videos[i]

            for (j in i + 1 until videos.size) {

                if (isCancelled()) {
                    return
                }

                val secondVideo = videos[j]

                val similarity =
                    calculateFingerprintSimilarity(
                        firstVideo.videoFingerprint,
                        secondVideo.videoFingerprint
                    )

                if (
                    similarity >= SIMILARITY_THRESHOLD
                ) {
                    similarPairCount++
                }

                comparisonCount++

                if (
                    comparisonCount % 100L == 0L ||
                    comparisonCount == totalComparisons
                ) {
                    onProgress(
                        "Comparing similar videos... " +
                                "$comparisonCount / " +
                                "$totalComparisons"
                    )
                }
            }
        }

        onProgress(
            "Similar video pairs found: $similarPairCount"
        )
    }

    // =============================================================
    // FINGERPRINT SIMILARITY
    // =============================================================

    private fun calculateFingerprintSimilarity(
        first: String,
        second: String
    ): Double {

        if (first.isEmpty() || second.isEmpty()) {
            return 0.0
        }

        if (first.length != second.length) {
            return 0.0
        }

        var totalDifference = 0L
        var comparedValues = 0

        var index = 0

        while (index < first.length) {

            val firstValue =
                first[index].hexValue()

            val secondValue =
                second[index].hexValue()

            totalDifference +=
                kotlin.math.abs(
                    firstValue - secondValue
                ).toLong()

            comparedValues++

            index++
        }

        if (comparedValues == 0) {
            return 0.0
        }

        // Each hexadecimal digit ranges from 0 to 15.
        val maximumDifference =
            comparedValues * 15.0

        val normalizedDifference =
            totalDifference.toDouble() /
                    maximumDifference

        return (
                1.0 - normalizedDifference
                ).coerceIn(0.0, 1.0)
    }

    private fun Char.hexValue(): Int {

        return when (this) {
            in '0'..'9' ->
                this - '0'

            in 'a'..'f' ->
                this - 'a' + 10

            in 'A'..'F' ->
                this - 'A' + 10

            else ->
                0
        }
    }

    // =============================================================
    // EXACT DUPLICATE DETECTION
    // =============================================================

    private fun findExactDuplicates(
        context: Context,
        dao: MediaDao,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean
    ) {

        val candidateSizes =
            dao.getDuplicateCandidateSizes()

        if (candidateSizes.isEmpty()) {
            onProgress(
                "No exact duplicate candidates found."
            )
            return
        }

        var processedSizes = 0

        for (size in candidateSizes) {

            if (isCancelled()) {
                return
            }

            val items =
                dao.getMediaWithSize(size)

            if (items.size < 2) {
                continue
            }

            val byQuickFingerprint =
                items.groupBy {
                    it.quickFingerprint
                }

            for ((_, quickGroup) in byQuickFingerprint) {

                if (isCancelled()) {
                    return
                }

                if (quickGroup.size < 2) {
                    continue
                }

                for (item in quickGroup) {

                    if (isCancelled()) {
                        return
                    }

                    val sha256 =
                        calculateSha256(
                            context,
                            Uri.parse(item.uri)
                        )

                    if (sha256.isNotEmpty()) {
                        dao.updateSha256(
                            item.uri,
                            sha256
                        )
                    }
                }
            }

            processedSizes++

            onProgress(
                "Checking exact duplicates... " +
                        "$processedSizes / " +
                        "${candidateSizes.size}"
            )
        }
    }

    // =============================================================
    // QUICK FINGERPRINT
    // =============================================================

    private fun createQuickFingerprint(
        resolver: ContentResolver,
        uri: Uri,
        size: Long
    ): String {

        return try {

            resolver.openInputStream(uri)?.use { input ->

                val digest =
                    MessageDigest.getInstance(
                        "SHA-256"
                    )

                val buffer =
                    ByteArray(8192)

                var remaining =
                    minOf(
                        QUICK_FINGERPRINT_SIZE.toLong(),
                        size
                    )

                while (remaining > 0) {

                    val read =
                        input.read(
                            buffer,
                            0,
                            minOf(
                                buffer.size.toLong(),
                                remaining
                            ).toInt()
                        )

                    if (read <= 0) {
                        break
                    }

                    digest.update(
                        buffer,
                        0,
                        read
                    )

                    remaining -= read
                }

                bytesToHex(
                    digest.digest()
                )

            } ?: ""

        } catch (_: Exception) {
            ""
        }
    }

    // =============================================================
    // SHA-256
    // =============================================================

    private fun calculateSha256(
        context: Context,
        uri: Uri
    ): String {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val buffer =
                        ByteArray(1024 * 1024)

                    while (true) {

                        val read =
                            input.read(buffer)

                        if (read <= 0) {
                            break
                        }

                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }

            bytesToHex(
                digest.digest()
            )

        } catch (_: Exception) {
            ""
        }
    }

    // =============================================================
    // PATH FILTERING
    // =============================================================

    private fun shouldSkipPath(
        relativePath: String,
        selectedPaths: List<String>,
        excludedPaths: List<String>,
        scanAll: Boolean
    ): Boolean {

        val normalized =
            normalizePath(relativePath)

        for (excluded in excludedPaths) {

            if (
                normalized == excluded ||
                normalized.startsWith("$excluded/")
            ) {
                return true
            }
        }

        if (scanAll) {
            return false
        }

        if (selectedPaths.isEmpty()) {
            return true
        }

        for (selected in selectedPaths) {

            if (
                normalized == selected ||
                normalized.startsWith("$selected/")
            ) {
                return false
            }
        }

        return true
    }

    // =============================================================
    // PATH NORMALIZATION
    // =============================================================

    private fun normalizePath(
        path: String
    ): String {

        return path
            .replace("\\", "/")
            .trim()
            .replace(Regex("/+"), "/")
            .trim('/')
            .lowercase(Locale.US)
    }

    // =============================================================
    // HEX
    // =============================================================

    private fun bytesToHex(
        bytes: ByteArray
    ): String {

        val chars =
            "0123456789abcdef"

        val result =
            StringBuilder(bytes.size * 2)

        for (byte in bytes) {

            val value =
                byte.toInt() and 0xFF

            result.append(
                chars[value ushr 4]
            )

            result.append(
                chars[value and 0x0F]
            )
        }

        return result.toString()
    }
}