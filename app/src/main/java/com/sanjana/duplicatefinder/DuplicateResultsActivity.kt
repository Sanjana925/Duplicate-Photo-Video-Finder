package com.sanjana.duplicatefinder

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.MediaStore
import java.util.Locale

class DuplicateResultsActivity : AppCompatActivity() {

    private lateinit var resultsSummaryText: TextView
    private lateinit var selectionSummaryText: TextView
    private lateinit var selectAllButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var groupsRecyclerView: RecyclerView

    private val selectedFiles =
        mutableSetOf<String>()

    private val allDuplicateItems =
        mutableListOf<DuplicateItem>()

    private lateinit var adapter: DuplicateGroupAdapter

    private val deleteLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

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
        super.onCreate(savedInstanceState)

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

        showSummary(
            photoGroups,
            videoGroups
        )

        /*
         * Only duplicate copies are deletable.
         *
         * The first file in every group is treated
         * as the copy to KEEP.
         */
        photoGroups.forEach { group ->

            if (group.size > 1) {

                allDuplicateItems.addAll(
                    group.drop(1)
                )
            }
        }

        videoGroups.forEach { group ->

            if (group.size > 1) {

                allDuplicateItems.addAll(
                    group.drop(1)
                )
            }
        }

        /*
         * Prepare RecyclerView data.
         */
        val groups =
            mutableListOf<DuplicateGroupAdapter.GroupItem>()

        photoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title = "📷 Photo Group ${index + 1}",
                    files = group,
                    isPhoto = true
                )
            )
        }

        videoGroups.forEachIndexed {
                index,
                group ->

            groups.add(
                DuplicateGroupAdapter.GroupItem(
                    title = "🎥 Video Group ${index + 1}",
                    files = group,
                    isPhoto = false
                )
            )
        }

        /*
         * RecyclerView.
         */
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

        /*
         * Select all / unselect all.
         */
        selectAllButton.setOnClickListener {

            selectOrUnselectAll()
        }

        /*
         * Delete selected.
         */
        deleteSelectedButton.setOnClickListener {

            deleteSelectedFiles()
        }

        updateSelectionSummary()
    }

    private fun showSummary(
        photoGroups: List<List<DuplicateItem>>,
        videoGroups: List<List<DuplicateItem>>
    ) {

        val totalGroups =
            photoGroups.size +
                    videoGroups.size

        val totalDuplicates =
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
                    "Duplicate groups: "
                )

                append(
                    totalGroups
                )

                append(
                    "\nDuplicate files: "
                )

                append(
                    totalDuplicates
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

        if (shouldSelect) {

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

        if (
            selectedItems.size ==
            allDuplicateItems.size &&
            allDuplicateItems.isNotEmpty()
        ) {

            selectAllButton.text =
                "UNSELECT ALL"

        } else {

            selectAllButton.text =
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

        if (selectedItems.isEmpty()) {

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

        if (uris.isEmpty()) {

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
    }
}