package com.sanjana.duplicatefinder

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DuplicateResultsActivity : AppCompatActivity() {

    private lateinit var resultsSummaryText: TextView
    private lateinit var selectionSummaryText: TextView
    private lateinit var selectAllButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var groupsRecyclerView: RecyclerView

    private val selectedFiles =
        mutableSetOf<String>()

    /*
     * ALL exact duplicate copies are kept here.
     *
     * This allows the user to decide which
     * physical copy should be deleted.
     */
    private val allDuplicateItems =
        mutableListOf<DuplicateItem>()

    private lateinit var adapter:
            DuplicateGroupAdapter

    private val deleteLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (
                result.resultCode ==
                Activity.RESULT_OK
            ) {

                handleSuccessfulDeletion()

            } else {

                Toast.makeText(
                    this,
                    "Deletion cancelled.",
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
            R.layout.activity_duplicate_results
        )

        resultsSummaryText =
            findViewById(
                R.id.resultsSummaryText
            )

        selectionSummaryText =
            findViewById(
                R.id.selectionSummaryText
            )

        selectAllButton =
            findViewById(
                R.id.selectAllButton
            )

        deleteSelectedButton =
            findViewById(
                R.id.deleteSelectedButton
            )

        groupsRecyclerView =
            findViewById(
                R.id.groupsRecyclerView
            )

        val photoGroups =
            intent.getSerializableExtra(
                EXTRA_PHOTO_GROUPS
            ) as? ArrayList<ArrayList<DuplicateItem>>
                ?: arrayListOf()

        val videoGroups =
            intent.getSerializableExtra(
                EXTRA_VIDEO_GROUPS
            ) as? ArrayList<ArrayList<DuplicateItem>>
                ?: arrayListOf()

        val similarPhotoGroups =
            intent.getSerializableExtra(
                EXTRA_SIMILAR_PHOTO_GROUPS
            ) as? ArrayList<ArrayList<DuplicateItem>>
                ?: arrayListOf()

        val dateTimePhotoGroups =
            intent.getSerializableExtra(
                EXTRA_DATE_TIME_PHOTO_GROUPS
            ) as? ArrayList<ArrayList<DuplicateItem>>
                ?: arrayListOf()

        showSummary(
            photoGroups,
            videoGroups,
            similarPhotoGroups,
            dateTimePhotoGroups
        )

        /*
         * Add EVERY exact duplicate file.
         *
         * The first copy starts unchecked.
         * The other copies start checked.
         */
        photoGroups.forEach { group ->

            if (
                group.size > 1
            ) {

                allDuplicateItems.addAll(
                    group
                )

                group.drop(1).forEach { file ->

                    selectedFiles.add(
                        file.uri
                    )
                }
            }
        }

        videoGroups.forEach { group ->

            if (
                group.size > 1
            ) {

                allDuplicateItems.addAll(
                    group
                )

                group.drop(1).forEach { file ->

                    selectedFiles.add(
                        file.uri
                    )
                }
            }
        }

        val groups =
            mutableListOf<
                    DuplicateGroupAdapter.GroupItem
                    >()

        photoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title =
                        "📷 Exact Photo Group ${index + 1}",
                    files = group,
                    isPhoto = true,
                    isSimilar = false,
                    isDateTime = false
                )
            )
        }

        videoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title =
                        "🎥 Exact Video Group ${index + 1}",
                    files = group,
                    isPhoto = false,
                    isSimilar = false,
                    isDateTime = false
                )
            )
        }

        similarPhotoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title =
                        "🖼️ Similar Photo Group ${index + 1}",
                    files = group,
                    isPhoto = true,
                    isSimilar = true,
                    isDateTime = false
                )
            )
        }

        dateTimePhotoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title =
                        "🕐 Date/Time Photo Group ${index + 1}",
                    files = group,
                    isPhoto = true,
                    isSimilar = false,
                    isDateTime = true
                )
            )
        }

        groupsRecyclerView.layoutManager =
            LinearLayoutManager(this)

        adapter =
            DuplicateGroupAdapter(
                activity = this,
                groups = groups,
                selectedFiles = selectedFiles,
                onSelectionChanged = {
                    updateSelectionSummary()
                }
            )

        groupsRecyclerView.adapter =
            adapter

        selectAllButton.setOnClickListener {
            selectOrUnselectAll()
        }

        deleteSelectedButton.setOnClickListener {
            deleteSelectedFiles()
        }

        updateSelectionSummary()
    }

    private fun showSummary(
        photoGroups:
        List<List<DuplicateItem>>,
        videoGroups:
        List<List<DuplicateItem>>,
        similarPhotoGroups:
        List<List<DuplicateItem>>,
        dateTimePhotoGroups:
        List<List<DuplicateItem>>
    ) {

        val exactGroups =
            photoGroups.size +
                    videoGroups.size

        val exactDuplicates =
            photoGroups.sumOf {
                maxOf(
                    0,
                    it.size - 1
                )
            } +
                    videoGroups.sumOf {
                        maxOf(
                            0,
                            it.size - 1
                        )
                    }

        val recoverableBytes =
            photoGroups.sumOf { group ->

                group
                    .drop(1)
                    .sumOf {
                        it.size
                    }
            } +
                    videoGroups.sumOf { group ->

                        group
                            .drop(1)
                            .sumOf {
                                it.size
                            }
                    }

        resultsSummaryText.text =
            buildString {

                append(
                    "Exact duplicate groups: "
                )

                append(
                    exactGroups
                )

                append(
                    "\nExact duplicate files: "
                )

                append(
                    exactDuplicates
                )

                append(
                    "\nSimilar photo groups: "
                )

                append(
                    similarPhotoGroups.size
                )

                append(
                    "\nDate/time photo groups: "
                )

                append(
                    dateTimePhotoGroups.size
                )

                append(
                    "\nPotential recovery: "
                )

                append(
                    formatBytes(
                        recoverableBytes
                    )
                )
            }
    }

    private fun selectOrUnselectAll() {

        val shouldSelect =
            selectedFiles.size <
                    allDuplicateItems.size

        selectedFiles.clear()

        if (
            shouldSelect
        ) {

            allDuplicateItems.forEach { file ->

                selectedFiles.add(
                    file.uri
                )
            }
        }

        adapter.notifyDataSetChanged()

        updateSelectionSummary()
    }

    private fun updateSelectionSummary() {

        val selectedItems =
            allDuplicateItems.filter { file ->

                selectedFiles.contains(
                    file.uri
                )
            }

        val selectedBytes =
            selectedItems.sumOf {
                it.size
            }

        selectionSummaryText.text =
            buildString {

                append(
                    "Selected: "
                )

                append(
                    selectedItems.size
                )

                append(
                    " files • "
                )

                append(
                    formatBytes(
                        selectedBytes
                    )
                )
            }

        selectAllButton.text =
            if (
                selectedItems.size ==
                allDuplicateItems.size &&
                allDuplicateItems.isNotEmpty()
            ) {

                "UNSELECT ALL"

            } else {

                "SELECT ALL DUPLICATES"
            }

        deleteSelectedButton.isEnabled =
            selectedItems.isNotEmpty()

        deleteSelectedButton.text =
            if (
                selectedItems.isEmpty()
            ) {

                "DELETE SELECTED"

            } else {

                "DELETE ${selectedItems.size} SELECTED"
            }
    }

    private fun deleteSelectedFiles() {

        val selectedItems =
            allDuplicateItems.filter { file ->

                selectedFiles.contains(
                    file.uri
                )
            }

        if (
            selectedItems.isEmpty()
        ) {

            Toast.makeText(
                this,
                "No files selected.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val uris =
            selectedItems.mapNotNull { file ->

                if (
                    file.uri.isBlank()
                ) {

                    null

                } else {

                    Uri.parse(
                        file.uri
                    )
                }
            }

        if (
            uris.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Unable to find media URIs for deletion.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            val pendingIntent =
                MediaStore.createDeleteRequest(
                    contentResolver,
                    uris
                )

            val request =
                IntentSenderRequest.Builder(
                    pendingIntent.intentSender
                ).build()

            deleteLauncher.launch(
                request
            )

        } else {

            deleteFilesLegacy(
                uris
            )
        }
    }

    private fun deleteFilesLegacy(
        uris: List<Uri>
    ) {

        var deletedCount =
            0

        uris.forEach { uri ->

            try {

                val deleted =
                    contentResolver.delete(
                        uri,
                        null,
                        null
                    )

                if (
                    deleted > 0
                ) {

                    deletedCount++
                }

            } catch (_: Exception) {
                // Ignore individual failures.
            }
        }

        if (
            deletedCount > 0
        ) {

            removeDeletedItems(
                uris
            )

            Toast.makeText(
                this,
                "$deletedCount files deleted.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            Toast.makeText(
                this,
                "No files were deleted.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleSuccessfulDeletion() {

        val selectedItems =
            allDuplicateItems.filter { file ->

                selectedFiles.contains(
                    file.uri
                )
            }

        val deletedUris =
            selectedItems.mapNotNull { file ->

                if (
                    file.uri.isBlank()
                ) {

                    null

                } else {

                    Uri.parse(
                        file.uri
                    )
                }
            }

        removeDeletedItems(
            deletedUris
        )

        Toast.makeText(
            this,
            "${deletedUris.size} files deleted.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun removeDeletedItems(
        deletedUris: List<Uri>
    ) {

        val deletedUriStrings =
            deletedUris
                .map {
                    it.toString()
                }
                .toSet()

        allDuplicateItems.removeAll { file ->

            deletedUriStrings.contains(
                file.uri
            )
        }

        selectedFiles.clear()

        Toast.makeText(
            this,
            "Deletion complete. Please scan again to refresh results.",
            Toast.LENGTH_LONG
        ).show()

        finish()
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

    data class DuplicateItem(
        val name: String,
        val relativePath: String,
        val size: Long,
        val width: Int,
        val height: Int,
        val sha256: String,
        val uri: String = ""
    ) : java.io.Serializable

    companion object {

        const val EXTRA_PHOTO_GROUPS =
            "photo_duplicate_groups"

        const val EXTRA_VIDEO_GROUPS =
            "video_duplicate_groups"

        const val EXTRA_SIMILAR_PHOTO_GROUPS =
            "similar_photo_groups"

        const val EXTRA_DATE_TIME_PHOTO_GROUPS =
            "date_time_photo_groups"
    }
}

