package com.sanjana.duplicatefinder

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
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

    class GroupViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val groupTitleText: TextView =
            itemView.findViewById(R.id.groupTitleText)

        val groupInfoText: TextView =
            itemView.findViewById(R.id.groupInfoText)

        val groupFilesContainer: LinearLayout =
            itemView.findViewById(R.id.groupFilesContainer)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GroupViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_duplicate_group,
            parent,
            false
        )

        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: GroupViewHolder,
        position: Int
    ) {

        val group = groups[position]

        holder.groupTitleText.text = group.title

        if (group.isDateTime) {

            holder.groupInfoText.text =
                "${group.files.size} photos close in time • review only"

        } else if (group.isSimilar) {

            holder.groupInfoText.text =
                "${group.files.size} visually similar photos • review only"

        } else {

            val recoverableBytes =
                group.files
                    .drop(1)
                    .sumOf { it.size }

            holder.groupInfoText.text = buildString {

                append(group.files.size)
                append(" copies • ")
                append(formatBytes(recoverableBytes))
                append(" recoverable")
            }
        }

        /*
         * RecyclerView reuses ViewHolders.
         *
         * Remove old file rows before creating
         * the rows for this group.
         */
        holder.groupFilesContainer.removeAllViews()

        group.files.forEachIndexed { index, file ->

            addFileRow(
                container = holder.groupFilesContainer,
                file = file,
                group = group,
                index = index
            )
        }
    }

    override fun getItemCount(): Int =
        groups.size

    /*
     * Returns the groups currently held by this adapter.
     */
    fun getGroups(): List<GroupItem> {
        return groups
    }

    /*
     * Refresh only currently visible groups.
     *
     * Used by SELECT ALL / UNSELECT ALL.
     *
     * We intentionally do not call notifyDataSetChanged()
     * because that would rebuild all thumbnails.
     */
    fun refreshVisibleSelections() {

        val recyclerView =
            activity.findViewById<RecyclerView>(
                R.id.groupsRecyclerView
            )

        if (recyclerView == null) {

            onSelectionChanged()
            return
        }

        for (position in 0 until itemCount) {

            val holder =
                recyclerView
                    .findViewHolderForAdapterPosition(position)
                        as? GroupViewHolder
                    ?: continue

            val group = groups[position]

            /*
             * Similar and date/time groups are
             * review-only and have no checkboxes.
             */
            if (
                group.isSimilar ||
                group.isDateTime
            ) {
                continue
            }

            val container =
                holder.groupFilesContainer

            /*
             * One child row exists for every file.
             */
            for (
            childIndex in 0 until container.childCount
            ) {

                if (childIndex >= group.files.size) {
                    break
                }

                val row =
                    container.getChildAt(childIndex)

                val checkBox =
                    row.findViewWithTag<CheckBox>(
                        CHECKBOX_TAG
                    )

                if (checkBox == null) {
                    continue
                }

                val file =
                    group.files[childIndex]

                val shouldBeChecked =
                    selectedFiles.contains(file.uri)

                /*
                 * Prevent programmatic isChecked changes
                 * from triggering the listener.
                 */
                checkBox.setOnCheckedChangeListener(null)

                checkBox.isChecked =
                    shouldBeChecked

                checkBox.setOnCheckedChangeListener {
                        _,
                        checked ->

                    handleIndividualSelection(
                        group = group,
                        file = file,
                        checked = checked,
                        checkBox = checkBox
                    )
                }

                updateStatusText(
                    row = row,
                    group = group,
                    file = file
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

        val row = LinearLayout(activity)

        row.orientation =
            LinearLayout.HORIZONTAL

        row.gravity =
            android.view.Gravity.CENTER_VERTICAL

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

        /*
         * Thumbnail
         */
        val thumbnail =
            ImageView(activity)

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
            file = file,
            imageView = thumbnail
        )

        row.addView(thumbnail)

        /*
         * Information container
         */
        val infoContainer =
            LinearLayout(activity)

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

        /*
         * KEEP / DELETE / REVIEW status
         */
        val statusText =
            TextView(activity)

        statusText.tag =
            STATUS_TAG

        statusText.textSize =
            12f

        statusText.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        infoContainer.addView(statusText)

        /*
         * File name
         */
        val nameText =
            TextView(activity)

        nameText.text =
            file.name

        nameText.textSize =
            14f

        nameText.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        nameText.setTextColor(
            activity.getColor(
                R.color.text_primary
            )
        )

        nameText.maxLines =
            2

        infoContainer.addView(nameText)

        /*
         * File path
         */
        val pathText =
            TextView(activity)

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
            android.text.TextUtils.TruncateAt.MIDDLE

        infoContainer.addView(pathText)

        /*
         * File details
         */
        val detailsText =
            TextView(activity)

        detailsText.text =
            buildString {

                append(
                    formatBytes(file.size)
                )

                if (
                    file.width > 0 &&
                    file.height > 0
                ) {

                    append(" • ")

                    append(file.width)

                    append(" × ")

                    append(file.height)
                }
            }

        detailsText.textSize =
            11f

        detailsText.setTextColor(
            activity.getColor(
                R.color.text_secondary
            )
        )

        infoContainer.addView(detailsText)

        /*
         * Date/time review information.
         */
        if (group.isDateTime) {

            val reviewText =
                TextView(activity)

            reviewText.text =
                "Photos added close together"

            reviewText.textSize =
                11f

            reviewText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            infoContainer.addView(reviewText)
        }

        row.addView(infoContainer)

        /*
         * Only exact duplicate groups have
         * selectable checkboxes.
         *
         * Similar and date/time groups are
         * review-only.
         */
        if (
            !group.isSimilar &&
            !group.isDateTime
        ) {

            val checkBox =
                CheckBox(activity)

            checkBox.tag =
                CHECKBOX_TAG

            checkBox.isChecked =
                selectedFiles.contains(file.uri)

            checkBox.contentDescription =
                "Select ${file.name} for deletion"

            checkBox.setOnCheckedChangeListener {
                    _,
                    checked ->

                handleIndividualSelection(
                    group = group,
                    file = file,
                    checked = checked,
                    checkBox = checkBox
                )
            }

            row.addView(checkBox)
        }

        /*
         * Set initial KEEP / DELETE / REVIEW status.
         */
        updateStatusText(
            row = row,
            group = group,
            file = file
        )

        container.addView(row)
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
            selectedFiles.contains(file.uri)

        /*
         * Date/time review group.
         */
        if (group.isDateTime) {

            statusText.text =
                "🕐 CLOSE IN TIME — REVIEW"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            return
        }

        /*
         * Similar-photo review group.
         */
        if (group.isSimilar) {

            statusText.text =
                "SIMILAR — REVIEW"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

            return
        }

        /*
         * Exact duplicate.
         */
        if (isSelected) {

            statusText.text =
                "DELETE"

            statusText.setTextColor(
                activity.getColor(
                    R.color.text_secondary
                )
            )

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

        /*
         * A group with one file cannot be a duplicate group.
         */
        if (group.files.size <= 1) {
            return
        }

        if (checked) {

            /*
             * Count files already selected
             * in this exact group.
             */
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

            /*
             * Never allow every physical copy
             * in a group to be selected.
             *
             * Example:
             *
             * 3 copies
             * maximum selectable = 2
             *
             * 2 copies
             * maximum selectable = 1
             */
            if (
                !isAlreadySelected &&
                currentlySelectedInGroup >=
                group.files.size - 1
            ) {

                checkBox.setOnCheckedChangeListener(null)

                checkBox.isChecked =
                    false

                checkBox.setOnCheckedChangeListener {
                        _,
                        newChecked ->

                    handleIndividualSelection(
                        group = group,
                        file = file,
                        checked = newChecked,
                        checkBox = checkBox
                    )
                }

                Toast.makeText(
                    activity,
                    "At least one copy must remain.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            selectedFiles.add(file.uri)

        } else {

            selectedFiles.remove(file.uri)
        }

        /*
         * Update this row's KEEP / DELETE status.
         */
        updateStatusText(
            row = checkBox.parent as View,
            group = group,
            file = file
        )

        onSelectionChanged()
    }

    private fun loadThumbnail(
        file: DuplicateResultsActivity.DuplicateItem,
        imageView: ImageView
    ) {

        if (file.uri.isBlank()) {
            return
        }

        val uri =
            Uri.parse(file.uri)

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
                            .openInputStream(uri)
                            ?.use { input ->

                                BitmapFactory
                                    .decodeStream(input)
                            }
                    }

                } catch (_: Exception) {

                    null
                }

            if (bitmap != null) {

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

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        activity.resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    private fun formatBytes(
        bytes: Long
    ): String {

        if (bytes < 1024) {
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