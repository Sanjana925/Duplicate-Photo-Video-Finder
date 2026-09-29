package com.sanjana.duplicatefinder.scanner

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.sanjana.duplicatefinder.database.AppDatabase
import com.sanjana.duplicatefinder.database.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest

class ScanManager(
    private val contentResolver: ContentResolver,
    private val database: AppDatabase
) {

    private val dao = database.mediaDao()

    suspend fun scan(
        scanAll: Boolean,
        selectedFolderPaths: List<String>,
        excludedFolderPaths: List<String>,
        onProgress: (String, Int) -> Unit
    ): ScanSummary = withContext(Dispatchers.IO) {

        /*
         * Phase 1 intentionally rebuilds the persistent index
         * for each scan.
         *
         * Later we will add incremental indexing.
         */
        dao.deleteAll()

        val scanTimestamp = System.currentTimeMillis()

        onProgress(
            "Indexing your media library...",
            2
        )

        val photoCount = indexImages(
            scanAll = scanAll,
            selectedFolderPaths = selectedFolderPaths,
            excludedFolderPaths = excludedFolderPaths,
            scanTimestamp = scanTimestamp,
            onProgress = onProgress
        )

        currentCoroutineContext().ensureActive()

        val videoCount = indexVideos(
            scanAll = scanAll,
            selectedFolderPaths = selectedFolderPaths,
            excludedFolderPaths = excludedFolderPaths,
            scanTimestamp = scanTimestamp,
            onProgress = onProgress
        )

        currentCoroutineContext().ensureActive()

        onProgress(
            "Finding same-size candidates...",
            12
        )

        val candidateSizes =
            dao.getDuplicateCandidateSizes()

        if (candidateSizes.isEmpty()) {

            onProgress(
                "No same-size candidates found.",
                100
            )

            return@withContext ScanSummary(
                photoCount = photoCount,
                videoCount = videoCount,
                candidateFiles = 0
            )
        }

        var candidateFiles = 0

        candidateSizes.forEachIndexed { index, size ->

            currentCoroutineContext().ensureActive()

            val files =
                dao.getMediaWithSize(size)

            candidateFiles += files.size

            val baseProgress =
                12 +
                        (
                                index.toDouble() /
                                        candidateSizes.size.coerceAtLeast(1) *
                                        38
                                ).toInt()

            onProgress(
                "Quick-checking candidate files...",
                baseProgress.coerceIn(12, 50)
            )

            processQuickFingerprints(files)
        }

        currentCoroutineContext().ensureActive()

        onProgress(
            "Finding files with matching fingerprints...",
            55
        )

        val fullHashCandidates =
            getFullHashCandidates(candidateSizes)

        currentCoroutineContext().ensureActive()

        if (fullHashCandidates.isEmpty()) {

            onProgress(
                "No exact duplicate candidates found.",
                100
            )

            return@withContext ScanSummary(
                photoCount = photoCount,
                videoCount = videoCount,
                candidateFiles = candidateFiles
            )
        }

        processFullHashes(
            fullHashCandidates,
            onProgress
        )

        currentCoroutineContext().ensureActive()

        onProgress(
            "Finishing duplicate results...",
            100
        )

        ScanSummary(
            photoCount = photoCount,
            videoCount = videoCount,
            candidateFiles = candidateFiles
        )
    }

    /*
     * FIX:
     * This function is now suspend because it uses
     * currentCoroutineContext().ensureActive().
     */
    private suspend fun indexImages(
        scanAll: Boolean,
        selectedFolderPaths: List<String>,
        excludedFolderPaths: List<String>,
        scanTimestamp: Long,
        onProgress: (String, Int) -> Unit
    ): Int {

        val collection =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

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

        var count = 0

        val batch =
            ArrayList<MediaEntity>(
                INSERT_BATCH_SIZE
            )

        contentResolver.query(
            collection,
            projection,
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

            while (cursor.moveToNext()) {

                currentCoroutineContext().ensureActive()

                val relativePath =
                    cursor.getString(pathIndex) ?: ""

                if (
                    shouldSkipPath(
                        relativePath,
                        scanAll,
                        selectedFolderPaths,
                        excludedFolderPaths
                    )
                ) {
                    continue
                }

                val id =
                    cursor.getLong(idIndex)

                val uri =
                    Uri.withAppendedPath(
                        collection,
                        id.toString()
                    )


                batch.add(
                    MediaEntity(
                        uri.toString(),
                        cursor.getString(nameIndex) ?: "Unknown",
                        cursor.getString(mimeIndex) ?: "",
                        cursor.getLong(sizeIndex),
                        cursor.getLong(addedIndex),
                        cursor.getLong(modifiedIndex),
                        cursor.getInt(widthIndex),
                        cursor.getInt(heightIndex),
                        0L,
                        relativePath,
                        MEDIA_TYPE_PHOTO,
                        scanTimestamp
                    )
                )
                count++

                if (
                    batch.size >=
                    INSERT_BATCH_SIZE
                ) {

                    dao.insertAll(
                        ArrayList(batch)
                    )

                    batch.clear()

                    currentCoroutineContext()
                        .ensureActive()
                }

                if (
                    count % PROGRESS_UPDATE_INTERVAL == 0
                ) {

                    onProgress(
                        "Indexing photos... $count found",
                        3
                    )
                }
            }
        }

        currentCoroutineContext().ensureActive()

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
        }

        onProgress(
            "Indexed $count photos.",
            7
        )

        return count
    }

    /*
     * FIX:
     * This function is now suspend because it uses
     * currentCoroutineContext().ensureActive().
     */
    private suspend fun indexVideos(
        scanAll: Boolean,
        selectedFolderPaths: List<String>,
        excludedFolderPaths: List<String>,
        scanTimestamp: Long,
        onProgress: (String, Int) -> Unit
    ): Int {

        val collection =
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

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

        var count = 0

        val batch =
            ArrayList<MediaEntity>(
                INSERT_BATCH_SIZE
            )

        contentResolver.query(
            collection,
            projection,
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

            while (cursor.moveToNext()) {

                currentCoroutineContext().ensureActive()

                val relativePath =
                    cursor.getString(pathIndex) ?: ""

                if (
                    shouldSkipPath(
                        relativePath,
                        scanAll,
                        selectedFolderPaths,
                        excludedFolderPaths
                    )
                ) {
                    continue
                }

                val id =
                    cursor.getLong(idIndex)

                val uri =
                    Uri.withAppendedPath(
                        collection,
                        id.toString()
                    )

                batch.add(
                    MediaEntity(
                        uri.toString(),
                        cursor.getString(nameIndex) ?: "Unknown",
                        cursor.getString(mimeIndex) ?: "",
                        cursor.getLong(sizeIndex),
                        cursor.getLong(addedIndex),
                        cursor.getLong(modifiedIndex),
                        cursor.getInt(widthIndex),
                        cursor.getInt(heightIndex),
                        cursor.getLong(durationIndex),
                        relativePath,
                        MEDIA_TYPE_VIDEO,
                        scanTimestamp
                    )
                )

                count++

                if (
                    batch.size >=
                    INSERT_BATCH_SIZE
                ) {

                    dao.insertAll(
                        ArrayList(batch)
                    )

                    batch.clear()

                    currentCoroutineContext()
                        .ensureActive()
                }

                if (
                    count % PROGRESS_UPDATE_INTERVAL == 0
                ) {

                    onProgress(
                        "Indexing videos... $count found",
                        9
                    )
                }
            }
        }

        currentCoroutineContext().ensureActive()

        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
        }

        onProgress(
            "Indexed $count videos.",
            10
        )

        return count
    }

    private suspend fun processQuickFingerprints(
        files: List<MediaEntity>
    ) = coroutineScope {

        files
            .chunked(HASH_BATCH_SIZE)
            .forEach { batch ->

                currentCoroutineContext()
                    .ensureActive()

                batch
                    .map { file ->

                        async(Dispatchers.IO) {

                            val fingerprint =
                                calculateQuickFingerprint(
                                    Uri.parse(file.uri)
                                )

                            if (
                                fingerprint != null
                            ) {

                                dao.updateQuickFingerprint(
                                    file.uri,
                                    fingerprint
                                )
                            }
                        }
                    }
                    .awaitAll()
            }
    }

    private suspend fun getFullHashCandidates(
        candidateSizes: List<Long>
    ): List<MediaEntity> {

        val result =
            ArrayList<MediaEntity>()

        /*
         * We only retain files that share:
         *
         * size + quick fingerprint
         *
         * We do this one size group at a time.
         */
        for (size in candidateSizes) {

            currentCoroutineContext()
                .ensureActive()

            val files =
                dao.getMediaWithSize(size)

            val grouped =
                files
                    .filter {
                        it.quickFingerprint.isNotBlank()
                    }
                    .groupBy {
                        it.quickFingerprint
                    }

            grouped.values
                .filter {
                    it.size > 1
                }
                .forEach { group ->

                    result.addAll(group)
                }
        }

        return result
    }

    private suspend fun processFullHashes(
        files: List<MediaEntity>,
        onProgress: (String, Int) -> Unit
    ) = coroutineScope {

        val total =
            files.size

        var processed =
            0

        files
            .chunked(HASH_BATCH_SIZE)
            .forEach { batch ->

                currentCoroutineContext()
                    .ensureActive()

                batch
                    .map { file ->

                        async(Dispatchers.IO) {

                            val sha256 =
                                calculateSha256(
                                    Uri.parse(file.uri)
                                )

                            if (
                                sha256 != null
                            ) {

                                dao.updateSha256(
                                    file.uri,
                                    sha256
                                )
                            }
                        }
                    }
                    .awaitAll()

                processed += batch.size

                val progress =
                    55 +
                            (
                                    processed.toDouble() /
                                            total.coerceAtLeast(1) *
                                            45
                                    ).toInt()

                onProgress(
                    "Verifying exact duplicates... $processed / $total",
                    progress.coerceIn(55, 100)
                )
            }
    }

    private fun shouldSkipPath(
        relativePath: String,
        scanAll: Boolean,
        selectedFolderPaths: List<String>,
        excludedFolderPaths: List<String>
    ): Boolean {

        val normalized =
            normalizePath(relativePath)

        val excluded =
            excludedFolderPaths.any { excludedPath ->

                val path =
                    normalizePath(excludedPath)

                path.isNotBlank() &&
                        (
                                normalized == path ||
                                        normalized.startsWith("$path/")
                                )
            }

        if (excluded) {
            return true
        }

        if (scanAll) {
            return false
        }

        return !selectedFolderPaths.any { selectedPath ->

            val path =
                normalizePath(selectedPath)

            path.isBlank() ||
                    normalized == path ||
                    normalized.startsWith("$path/")
        }
    }

    private fun normalizePath(
        path: String
    ): String {

        return path
            .trim('/')
            .lowercase()
    }

    private fun calculateQuickFingerprint(
        uri: Uri
    ): String? {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            val descriptor =
                contentResolver.openFileDescriptor(
                    uri,
                    "r"
                )

            if (descriptor == null) {
                return calculateQuickFingerprintFallback(uri)
            }

            descriptor.use { parcelFileDescriptor ->

                FileInputStream(
                    parcelFileDescriptor.fileDescriptor
                ).use { input ->

                    val channel =
                        input.channel

                    val fileSize =
                        channel.size()

                    val sampleSize =
                        QUICK_SAMPLE_SIZE.toLong()

                    channel.position(0L)

                    val firstBuffer =
                        ByteArray(
                            QUICK_SAMPLE_SIZE
                        )

                    val firstRead =
                        input.read(firstBuffer)

                    if (firstRead > 0) {

                        digest.update(
                            firstBuffer,
                            0,
                            firstRead
                        )
                    }

                    if (fileSize > sampleSize) {

                        val lastPosition =
                            (
                                    fileSize -
                                            sampleSize
                                    ).coerceAtLeast(0L)

                        channel.position(
                            lastPosition
                        )

                        val lastBuffer =
                            ByteArray(
                                QUICK_SAMPLE_SIZE
                            )

                        val lastRead =
                            input.read(lastBuffer)

                        if (lastRead > 0) {

                            digest.update(
                                lastBuffer,
                                0,
                                lastRead
                            )
                        }
                    }
                }
            }

            digest.digest().toHex()

        } catch (_: Exception) {

            calculateQuickFingerprintFallback(uri)
        }
    }

    private fun calculateQuickFingerprintFallback(
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
                            QUICK_SAMPLE_SIZE
                        )

                    var remaining =
                        QUICK_SAMPLE_SIZE

                    while (remaining > 0) {

                        val read =
                            input.read(
                                buffer,
                                QUICK_SAMPLE_SIZE - remaining,
                                remaining
                            )

                        if (read <= 0) {
                            break
                        }

                        digest.update(
                            buffer,
                            QUICK_SAMPLE_SIZE - remaining,
                            read
                        )

                        remaining -= read
                    }
                }
                ?: return null

            digest.digest().toHex()

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
                            FULL_HASH_BUFFER_SIZE
                        )

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
                ?: return null

            digest.digest().toHex()

        } catch (_: Exception) {

            null
        }
    }

    data class ScanSummary(
        val photoCount: Int,
        val videoCount: Int,
        val candidateFiles: Int
    )

    companion object {

        private const val MEDIA_TYPE_PHOTO =
            "PHOTO"

        private const val MEDIA_TYPE_VIDEO =
            "VIDEO"

        private const val INSERT_BATCH_SIZE =
            500

        private const val HASH_BATCH_SIZE =
            8

        private const val PROGRESS_UPDATE_INTERVAL =
            250

        private const val QUICK_SAMPLE_SIZE =
            64 * 1024

        private const val FULL_HASH_BUFFER_SIZE =
            64 * 1024
    }
}

private fun ByteArray.toHex(): String {

    val result =
        StringBuilder(size * 2)

    for (byte in this) {

        result.append(
            "%02x".format(
                byte.toInt() and 0xff
            )
        )
    }

    return result.toString()
}