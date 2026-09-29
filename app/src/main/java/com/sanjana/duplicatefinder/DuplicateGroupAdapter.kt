package com.sanjana.duplicatefinder

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DuplicateGroupAdapter(
    private val activity: DuplicateResultsActivity,
    private val groups: List<GroupItem>,
    private val selectedFiles: MutableSet<String>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<DuplicateGroupAdapter.GroupViewHolder>() {

    data class GroupItem(
        val title: String,
        val files: List<DuplicateResultsActivity.DuplicateItem>,
        val isPhoto: Boolean,
        val isSimilar: Boolean,
        val isDateTime: Boolean
    )

    class GroupViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val groupTitleText: TextView =
            itemView.findViewById(
                R.id.groupTitleText
            )

        val groupInfoText: TextView =
            itemView.findViewById(
                R.id.groupInfoText
            )

        val groupFilesContainer: LinearLayout =
            itemView.findViewById(
                R.id.groupFilesContainer
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GroupViewHolder {

        val view =
            LayoutInflater.from(
                parent.context
            ).inflate(
                R.layout.item_duplicate_group,
                parent,
                false
            )

        return GroupViewHolder(
            view
        )
    }

    override fun onBindViewHolder(
        holder: GroupViewHolder,
        position: Int
    ) {

        val group =
            groups[position]

        holder.groupTitleText.text =
            group.title

        if (
            group.isDateTime
        ) {

            holder.groupInfoText.text =
                "${group.files.size} photos close in time • review only"

        } else if (
            group.isSimilar
        ) {

            holder.groupInfoText.text =
                "${group.files.size} visually similar photos • review only"

        } else {

            val recoverableBytes =
                group.files
                    .drop(1)
                    .sumOf {
                        it.size
                    }

            holder.groupInfoText.text =
                buildString {

                    append(
                        group.files.size
                    )

                    append(
                        " copies • "
                    )

                    append(
                        formatBytes(
                            recoverableBytes
                        )
                    )

                    append(
                        " recoverable"
                    )
                }
        }

        holder.groupFilesContainer
            .removeAllViews()

        group.files.forEachIndexed { index, file ->

            addFileRow(
                holder.groupFilesContainer,
                file,
                group,
                index
            )
        }
    }

    override fun getItemCount(): Int =
        groups.size

    fun getGroups(): List<GroupItem> =
        groups

    fun refreshVisibleSelections() {

        val recyclerView =
            activity.findViewById<RecyclerView>(
                R.id.groupsRecyclerView
            )

        if (
            recyclerView == null
        ) {

            onSelectionChanged()

            return
        }

        for (
        position in 0 until itemCount
        ) {

            val holder =
                recyclerView
                    .findViewHolderForAdapterPosition(
                        position
                    ) as? GroupViewHolder
                    ?: continue

            val group =
                groups[position]

            if (
                group.isSimilar ||
                group.isDateTime
            ) {
                continue
            }

            val container =
                holder.groupFilesContainer

            for (
            childIndex in 0 until container.childCount
            ) {

                if (
                    childIndex >=
                    group.files.size
                ) {
                    break
                }

                val row =
                    container.getChildAt(
                        childIndex
                    )

                val checkBox =
                    row.findViewWithTag<CheckBox>(
                        CHECKBOX_TAG
                    ) ?: continue

                val file =
                    group.files[
                        childIndex
                    ]

                val shouldBeChecked =
                    selectedFiles.contains(
                        file.uri
                    )

                checkBox.setOnCheckedChangeListener(
                    null
                )

                checkBox.isChecked =
                    shouldBeChecked

                checkBox.setOnCheckedChangeListener { _, checked ->

                    handleIndividualSelection(
                        group,
                        file,
                        checked,
                        checkBox
                    )
                }

                updateStatusText(
                    row,
                    group,
                    file
                )
            }
        }

        onSelectionChanged()
    }

    private fun addFileRow(
        container: LinearLayout,
        file: DuplicateResultsActivity.DuplicateItem,
        group: GroupItem,
        index: Int
    ) {

        val row =
            LinearLayout(
                activity
            )

        row.orientation =
            LinearLayout.HORIZONTAL

        row.gravity =
            Gravity.CENTER_VERTICAL

        row.setPadding(
            0,
            dp(8),
            0,
            dp(8)
        )

        row.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        val thumbnail =
            ImageView(
                activity
            )

        thumbnail.layoutParams =
            LinearLayout.LayoutParams(
                dp(76),
                dp(76)
            )

        thumbnail.scaleType =
            ImageView.ScaleType.CENTER_CROP

        thumbnail.setBackgroundColor(
            activity.getColor(
                R.color.screen_background
            )
        )

        loadThumbnail(
            file,
            thumbnail
        )

        row.addView(
            thumbnail
        )

        val infoContainer =
            LinearLayout(
                activity
            )

        infoContainer.orientation =
            LinearLayout.VERTICAL

        val infoParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

        infoParams.marginStart =
            dp(12)

        infoContainer.layoutParams =
            infoParams

        val statusText =
            TextView(
                activity
            )

        statusText.tag =
            STATUS_TAG

        statusText.textSize =
            12f

        statusText.setTypeface(
            null,
            Typeface.BOLD
        )

        infoContainer.addView(
            statusText
        )

        val nameText =
            TextView(
                activity
            )

        nameText.text =
            file.name

        nameText.textSize =
            14f

        nameText.setTypeface(
            null,
            Typeface.BOLD
        )

        nameText.setTextColor(
            activity.getColor(
                R.color.text_primary
            )
        )

        nameText.maxLines =
            2

        infoContainer.addView(
            nameText
        )

        val pathText =
            TextView(
                activity
            )

        pathText.text =
            file.relativePath.ifBlank {
                "Internal storage"
            }

        pathText.textSize =
            11f

        pathText.setTextColor(
            activity.getColor(
                R.color.text_secondary
            )
        )

        pathText.maxLines =
            2

        pathText.ellipsize =
            TextUtils.TruncateAt.MIDDLE

        infoContainer.addView(
            pathText
        )

        val detailsText =
            TextView(
                activity
            )

        detailsText.text =
            buildDetailsText(
                file
            )

        detailsText.textSize =
            11f

        detailsText.setTextColor(
            activity.getColor(
                R.color.text_secondary
            )
        )

        infoContainer.addView(
            detailsText
        )

        if (
            group.isDateTime
        ) {

            val reviewText =
                TextView(
                    activity
                )

            reviewText.text =
                "Photos added close together"

            reviewText.textSize =
                11f

            reviewText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            infoContainer.addView(
                reviewText
            )
        }

        row.addView(
            infoContainer
        )

        if (
            !group.isSimilar &&
            !group.isDateTime
        ) {

            val checkBox =
                CheckBox(
                    activity
                )

            checkBox.tag =
                CHECKBOX_TAG

            checkBox.isChecked =
                selectedFiles.contains(
                    file.uri
                )

            checkBox.contentDescription =
                "Select ${file.name} for deletion"

            checkBox.setOnCheckedChangeListener { _, checked ->

                handleIndividualSelection(
                    group,
                    file,
                    checked,
                    checkBox
                )
            }

            row.addView(
                checkBox
            )
        }

        updateStatusText(
            row,
            group,
            file
        )

        container.addView(
            row
        )
    }

    private fun buildDetailsText(
        file: DuplicateResultsActivity.DuplicateItem
    ): String {

        return buildString {

            append(
                formatBytes(
                    file.size
                )
            )

            if (
                file.width > 0 &&
                file.height > 0
            ) {

                append(
                    " • "
                )

                append(
                    file.width
                )

                append(
                    " × "
                )

                append(
                    file.height
                )
            }

            if (
                file.duration > 0L
            ) {

                append(
                    " • "
                )

                append(
                    formatDuration(
                        file.duration
                    )
                )
            }
        }
    }

    private fun updateStatusText(
        row: View,
        group: GroupItem,
        file: DuplicateResultsActivity.DuplicateItem
    ) {

        val statusText =
            row.findViewWithTag<TextView>(
                STATUS_TAG
            ) ?: return

        val isSelected =
            selectedFiles.contains(
                file.uri
            )

        if (
            group.isDateTime
        ) {

            statusText.text =
                "🕐 CLOSE IN TIME — REVIEW"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            return
        }

        if (
            group.isSimilar
        ) {

            statusText.text =
                "SIMILAR — REVIEW"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            return
        }

        if (
            isSelected
        ) {

            statusText.text =
                "DELETE"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                ))

        } else {

            statusText.text =
                "✓ KEEP"

            statusText.setTextColor(
                activity.getColor(
                    R.color.primary
                )
            )
        }
    }

    private fun handleIndividualSelection(
        group: GroupItem,
        file: DuplicateResultsActivity.DuplicateItem,
        checked: Boolean,
        checkBox: CheckBox
    ) {

        if (
            group.files.size <= 1
        ) {
            return
        }

        if (
            group.isSimilar ||
            group.isDateTime
        ) {

            checkBox.setOnCheckedChangeListener(
                null
            )

            checkBox.isChecked =
                false

            checkBox.setOnCheckedChangeListener { _, newChecked ->

                handleIndividualSelection(
                    group,
                    file,
                    newChecked,
                    checkBox
                )
            }

            return
        }

        if (
            checked
        ) {

            val currentlySelectedInGroup =
                group.files.count { item ->

                    selectedFiles.contains(
                        item.uri
                    )
                }

            val isAlreadySelected =
                selectedFiles.contains(
                    file.uri
                )

            if (
                !isAlreadySelected &&
                currentlySelectedInGroup >=
                group.files.size - 1
            ) {

                checkBox.setOnCheckedChangeListener(
                    null
                )

                checkBox.isChecked =
                    false

                checkBox.setOnCheckedChangeListener { _, newChecked ->

                    handleIndividualSelection(
                        group,
                        file,
                        newChecked,
                        checkBox
                    )
                }

                Toast.makeText(
                    activity,
                    "At least one copy must remain.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            selectedFiles.add(
                file.uri
            )

        } else {

            selectedFiles.remove(
                file.uri
            )
        }

        val row =
            checkBox.parent as? View

        if (
            row != null
        ) {

            updateStatusText(
                row,
                group,
                file
            )
        }

        onSelectionChanged()
    }

    private fun loadThumbnail(
        file: DuplicateResultsActivity.DuplicateItem,
        imageView: ImageView
    ) {

        if (
            file.uri.isBlank()
        ) {
            return
        }

        val uri =
            try {

                Uri.parse(
                    file.uri
                )

            } catch (_: Exception) {

                return
            }

        CoroutineScope(
            Dispatchers.IO
        ).launch {

            val bitmap =
                try {

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {

                        activity.contentResolver
                            .loadThumbnail(
                                uri,
                                Size(
                                    dp(180),
                                    dp(180)
                                ),
                                null
                            )

                    } else {

                        activity.contentResolver
                            .openInputStream(
                                uri
                            )
                            ?.use { input ->

                                BitmapFactory.decodeStream(
                                    input
                                )
                            }
                    }

                } catch (_: Exception) {

                    null
                }

            if (
                bitmap != null
            ) {

                withContext(
                    Dispatchers.Main
                ) {

                    if (
                        !activity.isFinishing &&
                        !activity.isDestroyed
                    ) {

                        imageView.setImageBitmap(
                            bitmap
                        )
                    }
                }
            }
        }
    }

    private fun formatDuration(
        durationMs: Long
    ): String {

        if (
            durationMs <= 0L
        ) {
            return ""
        }

        val totalSeconds =
            durationMs / 1000L

        val seconds =
            totalSeconds % 60L

        val totalMinutes =
            totalSeconds / 60L

        val minutes =
            totalMinutes % 60L

        val hours =
            totalMinutes / 60L

        return if (
            hours > 0L
        ) {

            String.format(
                Locale.US,
                "%d:%02d:%02d",
                hours,
                minutes,
                seconds
            )

        } else {

            String.format(
                Locale.US,
                "%d:%02d",
                minutes,
                seconds
            )
        }
    }

    private fun dp(
        value: Int
    ): Int =
        (
                value *
                        activity.resources
                            .displayMetrics
                            .density
                ).toInt()

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

    companion object {

        private const val CHECKBOX_TAG =
            "duplicate_selection_checkbox"

        private const val STATUS_TAG =
            "duplicate_status_text"
    }
}

