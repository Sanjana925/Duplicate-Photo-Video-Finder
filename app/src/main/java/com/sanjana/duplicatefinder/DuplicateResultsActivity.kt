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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sanjana.duplicatefinder.database.AppDatabase
import com.sanjana.duplicatefinder.database.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DuplicateResultsActivity :
    AppCompatActivity() {

    private lateinit var resultsSummaryText: TextView
    private lateinit var selectionSummaryText: TextView
    private lateinit var selectAllButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var groupsRecyclerView: RecyclerView

    private val selectedFiles =
        mutableSetOf<String>()

    private val allDuplicateItems =
        mutableListOf<DuplicateItem>()

    private lateinit var adapter:
            DuplicateGroupAdapter

    private lateinit var database:
            AppDatabase

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

        database =
            AppDatabaseProvider
                .getInstance(
                    this
                )

        groupsRecyclerView.layoutManager =
            LinearLayoutManager(
                this
            )

        selectAllButton.setOnClickListener {
            selectOrUnselectAll()
        }

        deleteSelectedButton.setOnClickListener {
            deleteSelectedFiles()
        }

        loadResults()
    }

    private fun loadResults() {

        lifecycleScope.launch {

            val entities =
                withContext(
                    Dispatchers.IO
                ) {

                    database
                        .mediaDao()
                        .getExactDuplicateItems()
                }

            val items =
                entities.map {
                    it.toDuplicateItem()
                }

            allDuplicateItems.clear()

            allDuplicateItems.addAll(
                items
            )

            val groups =
                buildGroups(
                    entities
                )

            adapter =
                DuplicateGroupAdapter(
                    activity =
                        this@DuplicateResultsActivity,

                    groups =
                        groups,

                    selectedFiles =
                        selectedFiles,

                    onSelectionChanged = {
                        updateSelectionSummary()
                    }
                )

            groupsRecyclerView.adapter =
                adapter

            showSummary(
                groups
            )

            updateSelectionSummary()

            if (
                groups.isEmpty()
            ) {

                Toast.makeText(
                    this@DuplicateResultsActivity,
                    "No exact duplicate photos or videos found.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun buildGroups(
        entities: List<MediaEntity>
    ):
            List<DuplicateGroupAdapter.GroupItem> {

        val groups =
            mutableListOf<
                    DuplicateGroupAdapter.GroupItem
                    >()

        val grouped =
            entities.groupBy {

                "${it.mediaType}|${it.sha256}"
            }

        var photoIndex =
            1

        var videoIndex =
            1

        grouped.values
            .filter {
                it.size > 1
            }
            .forEach { groupEntities ->

                val first =
                    groupEntities.first()

                val files =
                    groupEntities.map {
                        it.toDuplicateItem()
                    }

                if (
                    first.mediaType == "PHOTO"
                ) {

                    groups.add(
                        DuplicateGroupAdapter.GroupItem(
                            title =
                                "📷 Exact Photo Group $photoIndex",

                            files =
                                files,

                            isPhoto =
                                true,

                            isSimilar =
                                false,

                            isDateTime =
                                false
                        )
                    )

                    photoIndex++

                } else {

                    groups.add(
                        DuplicateGroupAdapter.GroupItem(
                            title =
                                "🎥 Exact Video Group $videoIndex",

                            files =
                                files,

                            isPhoto =
                                false,

                            isSimilar =
                                false,

                            isDateTime =
                                false
                        )
                    )

                    videoIndex++
                }
            }

        return groups
    }

    private fun showSummary(
        groups:
        List<
                DuplicateGroupAdapter.GroupItem
                >
    ) {

        val photoGroups =
            groups.filter {
                it.isPhoto
            }

        val videoGroups =
            groups.filter {
                !it.isPhoto
            }

        val exactDuplicates =
            groups.sumOf { group ->

                maxOf(
                    0,
                    group.files.size - 1
                )
            }

        val recoverableBytes =
            groups.sumOf { group ->

                group.files
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
                    groups.size
                )

                append(
                    "\nPhoto groups: "
                )

                append(
                    photoGroups.size
                )

                append(
                    "\nVideo groups: "
                )

                append(
                    videoGroups.size
                )

                append(
                    "\nExact duplicate files: "
                )

                append(
                    exactDuplicates
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

        val deletableCount =
            countDeletableDuplicateItems()

        if (
            deletableCount > 0 &&
            selectedFiles.size ==
            deletableCount
        ) {

            selectedFiles.clear()

        } else {

            selectedFiles.clear()

            val groups =
                adapter.getGroups()

            groups.forEach { group ->

                if (
                    !group.isSimilar &&
                    !group.isDateTime &&
                    group.files.size > 1
                ) {

                    group.files
                        .drop(1)
                        .forEach { file ->

                            if (
                                file.uri.isNotBlank()
                            ) {

                                selectedFiles.add(
                                    file.uri
                                )
                            }
                        }
                }
            }
        }

        adapter.refreshVisibleSelections()
    }

    private fun updateSelectionSummary() {

        val selectedItems =
            allDuplicateItems.filter {
                selectedFiles.contains(
                    it.uri
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

        val deletableCount =
            countDeletableDuplicateItems()

        selectAllButton.text =
            if (
                deletableCount > 0 &&
                selectedFiles.size ==
                deletableCount
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

    private fun countDeletableDuplicateItems():
            Int {

        return adapter
            .getGroups()
            .sumOf { group ->

                maxOf(
                    0,
                    group.files.size - 1
                )
            }
    }

    private fun deleteSelectedFiles() {

        val selectedItems =
            allDuplicateItems.filter {
                selectedFiles.contains(
                    it.uri
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

        if (
            wouldDeleteEveryCopyOfAnyGroup(
                selectedItems
            )
        ) {

            Toast.makeText(
                this,
                "At least one copy must remain in every duplicate group.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val uris =
            selectedItems.mapNotNull {

                if (
                    it.uri.isBlank()
                ) {
                    null
                } else {
                    Uri.parse(
                        it.uri
                    )
                }
            }

        if (
            uris.isEmpty()
        ) {

            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            try {

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

            } catch (
                exception: Exception
            ) {

                Toast.makeText(
                    this,
                    "Unable to request deletion: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        } else {

            deleteFilesLegacy(
                uris
            )
        }
    }

    private fun wouldDeleteEveryCopyOfAnyGroup(
        selectedItems:
        List<DuplicateItem>
    ): Boolean {

        val selectedUris =
            selectedItems
                .map {
                    it.uri
                }
                .toSet()

        return adapter
            .getGroups()
            .any { group ->

                val remaining =
                    group.files.count { file ->

                        !selectedUris.contains(
                            file.uri
                        )
                    }

                remaining == 0
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
            }
        }

        if (
            deletedCount > 0
        ) {

            removeDeletedItems(
                uris
            )

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
            allDuplicateItems.filter {
                selectedFiles.contains(
                    it.uri
                )
            }

        val deletedUris =
            selectedItems.mapNotNull {

                if (
                    it.uri.isBlank()
                ) {
                    null
                } else {
                    Uri.parse(
                        it.uri
                    )
                }
            }

        removeDeletedItems(
            deletedUris
        )
    }

    private fun removeDeletedItems(
        deletedUris: List<Uri>
    ) {

        val strings =
            deletedUris.map {
                it.toString()
            }

        lifecycleScope.launch {

            withContext(
                Dispatchers.IO
            ) {

                database
                    .mediaDao()
                    .deleteByUris(
                        strings
                    )
            }

            selectedFiles.clear()

            Toast.makeText(
                this@DuplicateResultsActivity,
                "${deletedUris.size} files deleted. Please scan again to refresh results.",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }

    private fun MediaEntity.toDuplicateItem():
            DuplicateItem {

        return DuplicateItem(

            name =
                name,

            relativePath =
                relativePath,

            size =
                size,

            width =
                width,

            height =
                height,

            sha256 =
                sha256,

            uri =
                uri
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

    data class DuplicateItem(
        val name: String,
        val relativePath: String,
        val size: Long,
        val width: Int,
        val height: Int,
        val sha256: String,
        val uri: String = ""
    ) : java.io.Serializable
}