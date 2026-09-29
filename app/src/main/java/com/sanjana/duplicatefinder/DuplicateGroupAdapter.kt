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
        val files:
        List<DuplicateResultsActivity.DuplicateItem>,
        val isPhoto: Boolean,
        val isSimilar: Boolean,
        val isDateTime: Boolean
    )

    class GroupViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

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
                buildString {

                    append(
                        group.files.size
                    )

                    append(
                        " photos close in time • review only"
                    )
                }

        } else if (
            group.isSimilar
        ) {

            holder.groupInfoText.text =
                buildString {

                    append(
                        group.files.size
                    )

                    append(
                        " visually similar photos • review only"
                    )
                }

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

        group.files.forEachIndexed {
                index,
                file ->

            addFileRow(
                holder.groupFilesContainer,
                file,
                isKeeper =
                    !group.isSimilar &&
                            !group.isDateTime &&
                            index == 0,
                isSimilar =
                    group.isSimilar,
                isDateTime =
                    group.isDateTime
            )
        }
    }

    override fun getItemCount(): Int =
        groups.size

    private fun addFileRow(
        container: LinearLayout,
        file:
        DuplicateResultsActivity.DuplicateItem,
        isKeeper: Boolean,
        isSimilar: Boolean,
        isDateTime: Boolean
    ) {

        val row =
            LinearLayout(
                activity
            )

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

        statusText.text =
            when {

                isDateTime ->
                    "🕐 CLOSE IN TIME — REVIEW"

                isSimilar ->
                    "SIMILAR — REVIEW"

                isKeeper ->
                    "✓ KEEP"

                else ->
                    "DELETE"
            }

        statusText.textSize =
            12f

        statusText.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        statusText.setTextColor(
            activity.getColor(
                if (
                    isKeeper
                ) {

                    R.color.primary

                } else {

                    R.color.text_secondary
                }
            )
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
            android.graphics.Typeface.BOLD
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
            android.text.TextUtils.TruncateAt.MIDDLE

        infoContainer.addView(
            pathText
        )

        val detailsText =
            TextView(
                activity
            )

        detailsText.text =
            buildString {

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
            }

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
            isDateTime
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

        /*
         * Exact duplicates:
         *
         * EVERY copy gets a checkbox.
         *
         * Similar and date/time groups remain
         * review-only and cannot be deleted
         * from this screen yet.
         */
        if (
            !isSimilar &&
            !isDateTime
        ) {

            val checkBox =
                CheckBox(
                    activity
                )

            val key =
                file.uri

            checkBox.isChecked =
                selectedFiles.contains(
                    key
                )

            checkBox.contentDescription =
                "Select ${file.name} for deletion"

            checkBox.setOnCheckedChangeListener {
                    _,
                    checked ->

                if (
                    checked
                ) {

                    selectedFiles.add(
                        key
                    )

                } else {

                    selectedFiles.remove(
                        key
                    )
                }

                onSelectionChanged()
            }

            row.addView(
                checkBox
            )
        }

        container.addView(
            row
        )
    }

    private fun loadThumbnail(
        file:
        DuplicateResultsActivity.DuplicateItem,
        imageView: ImageView
    ) {

        if (
            file.uri.isBlank()
        ) {
            return
        }

        val uri =
            Uri.parse(
                file.uri
            )

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

                                BitmapFactory
                                    .decodeStream(
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
}

