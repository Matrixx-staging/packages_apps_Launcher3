/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.recents.domain.usecase

import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import androidx.core.graphics.toRect
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData

/** This usecase is responsible for organizing desktop windows in a non-overlapping way. */
class OrganizeDesktopTasksUseCase {
    /**
     * Run to layout [taskBounds] within the screen [desktopBounds]. Layout is done in 2 stages:
     * 1. Optimal height is determined to maximize task window size. In this stage height is
     *    bisected to find maximum height which still allows all the windows to fit.
     * 2. Row widths are balanced. In this stage the available width is reduced until some windows
     *    are no longer fitting or until the difference between the narrowest and the widest rows
     *    starts growing. Overall this achieves the goals of maximum size for previews (or maximum
     *    row height which is equivalent assuming fixed height), balanced rows and minimal wasted
     *    space.
     * 3. Center the tasks within the available layout bounds.
     *
     * During layout, we apply minimum width (from layoutConfig.minTaskWidth) and minimum height (to
     * ensure no more than layoutConfig.maxRows) to the windows that can fit in the Overview grid.
     * For windows that cannot be placed by the layout algorithm (e.g., due to too many windows or
     * constraints) or had initially empty bounds, they will be assigned a small, fixed-size
     * placeholder Rect centered in `desktopBounds`. The `shouldBeDisplayedInOverview` field in the
     * returned [DesktopTaskBoundsData] will be `true` for successfully laid-out tasks and `false`
     * for tasks assigned placeholder bounds. For more details, please see b/421417134.
     *
     * @param desktopBounds The rectangular area on the screen where tasks should be laid out.
     * @param taskBounds A list of [DesktopTaskBoundsData] representing the tasks to be arranged,
     *   each with its original bounds and task ID. (Note: the `shouldBeDisplayedInOverview` field
     *   in the input `taskBounds` is ignored; it's determined by this use case).
     * @param layoutConfig Configuration parameters for the layout, such as margins, padding, and
     *   maximum number of rows.
     * @return A list of [DesktopTaskBoundsData] with updated `bounds` and a new
     *   `shouldBeDisplayedInOverview` flag.
     */
    operator fun invoke(
        desktopBounds: Rect,
        taskBounds: List<DesktopTaskBoundsData>,
        layoutConfig: DesktopLayoutConfig,
    ): List<DesktopTaskBoundsData> {
        if (desktopBounds.isEmpty || taskBounds.isEmpty()) {
            return emptyList()
        }

        // Filter out [taskBounds] with empty rects before calculating layout.
        val validTaskBounds = taskBounds.filterNot { it.bounds.isEmpty }

        if (validTaskBounds.isEmpty()) {
            // All tasks had initially empty bounds.
            val placeholderBounds = createPlaceholderBounds(desktopBounds, layoutConfig)
            return taskBounds.map {
                DesktopTaskBoundsData(
                    taskId = it.taskId,
                    bounds = placeholderBounds,
                    shouldBeDisplayedInOverview = false,
                )
            }
        }

        // Assuming we can place all windows in one row, do one pass first to check whether all
        // windows can fit.
        // Use [validTaskBounds] here to calculate the desired effective layout bounds.
        var availableLayoutBounds =
            desktopBounds.getLayoutEffectiveBounds(
                singleRow = true,
                taskNumber = validTaskBounds.size,
                layoutConfig,
            )
        var resultRects =
            findOptimalHeightAndBalancedWidth(availableLayoutBounds, validTaskBounds, layoutConfig)

        // If the windows can't fit in one row, try to fit them in multiple rows.
        if (!canFitInOneRow(resultRects)) {
            availableLayoutBounds =
                desktopBounds.getLayoutEffectiveBounds(
                    singleRow = false,
                    taskNumber = validTaskBounds.size,
                    layoutConfig,
                )
            resultRects =
                findOptimalHeightAndBalancedWidth(
                    availableLayoutBounds,
                    validTaskBounds,
                    layoutConfig,
                )
        }

        val successfullyLaidOutRectFs = resultRects.filter { !it.isEmpty }
        if (successfullyLaidOutRectFs.isNotEmpty()) {
            val maxBottom = successfullyLaidOutRectFs.maxOfOrNull { it.bottom } ?: 0f
            centerTaskWindows(
                availableLayoutBounds,
                maxBottom.toInt(),
                successfullyLaidOutRectFs,
                layoutConfig,
            )
        }

        // Construct the final result list.
        return taskBounds.mapIndexed { index, originalTaskData ->
            val resultRectF = resultRects[index]
            if (!resultRectF.isEmpty) {
                DesktopTaskBoundsData(
                    taskId = originalTaskData.taskId,
                    bounds = resultRectF.toRect(),
                    shouldBeDisplayedInOverview = true,
                )
            } else {
                val placeholderBounds = createPlaceholderBounds(desktopBounds, layoutConfig)
                DesktopTaskBoundsData(
                    taskId = originalTaskData.taskId,
                    bounds = placeholderBounds,
                    shouldBeDisplayedInOverview = false,
                )
            }
        }
    }

    /**
     * Creates a small, square placeholder Rect centered within the given [desktopBounds]. The size
     * of the square is determined by `layoutConfig.minTaskWidth`. Used for tasks that cannot be
     * laid out due to the size constraints.
     */
    private fun createPlaceholderBounds(
        desktopBounds: Rect,
        layoutConfig: DesktopLayoutConfig,
    ): Rect {
        if (desktopBounds.isEmpty) {
            // If desktopBounds is empty, we can't really center. Return an empty rect.
            return Rect()
        }
        val size = minOf(layoutConfig.minTaskWidth, desktopBounds.width(), desktopBounds.height())
        return Rect().apply { Gravity.apply(Gravity.CENTER, size, size, desktopBounds, this) }
    }

    /**
     * Determines the optimal height for task windows and balances the row widths to minimize wasted
     * space. Returns the bounds for each task window after layout.
     */
    private fun findOptimalHeightAndBalancedWidth(
        availableLayoutBounds: Rect,
        validTaskBounds: List<DesktopTaskBoundsData>,
        layoutConfig: DesktopLayoutConfig,
    ): List<RectF> {
        // Right bound of the narrowest row.
        var minRight: Int
        // Right bound of the widest row.
        var maxRight: Int

        // Keep track of the difference between the narrowest and the widest row.
        // Initially this is set to the worst it can ever be assuming the windows fit.
        var widthDiff = availableLayoutBounds.width()

        // Initially allow the windows to occupy all available width. Shrink this available space
        // horizontally to find the breakdown into rows that achieves the minimal [widthDiff].
        var rightBound = availableLayoutBounds.right

        // Determine the optimal height bisecting between [lowHeight] and [highHeight]. Once this
        // optimal height is known, [heightFixed] is set to `true` and the rows are balanced by
        // repeatedly squeezing the widest row to cause windows to overflow to the subsequent rows.
        var lowHeight =
            maxOf(
                DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(
                    availableLayoutBounds,
                    layoutConfig,
                ),
                DesktopLayoutUtils.getRequiredHeightForMinWidth(validTaskBounds, layoutConfig),
            )
        var highHeight = maxOf(lowHeight, availableLayoutBounds.height() + 1)
        var optimalHeight = 0.5f * (lowHeight + highHeight)
        var heightFixed = false

        // Repeatedly try to fit the windows [resultRects] within [rightBound]. If a maximum
        // [optimalHeight] is found such that all window [resultRects] fit, this fitting continues
        // while shrinking the [rightBound] in order to balance the rows. If the windows fit the
        // [rightBound] would have been decremented at least once so it needs to be incremented once
        // before getting out of this loop and one additional pass made to actually fit the
        // [resultRects]. If the [resultRects] cannot fit (e.g. there are too many windows) the
        // bisection will still finish and we might increment the [rightBound] one pixel extra
        // which is acceptable since there is an unused margin on the right.
        var makeLastAdjustment = false
        var resultRects: List<RectF>

        while (true) {
            val fitWindowResult =
                fitWindowRectsInBounds(
                    Rect(availableLayoutBounds).apply { right = rightBound },
                    validTaskBounds,
                    minOf(
                        DesktopLayoutUtils.getMaxTaskHeight(availableLayoutBounds),
                        optimalHeight.toInt(),
                    ),
                    layoutConfig,
                )
            val allWindowsFit = fitWindowResult.allWindowsFit
            resultRects = fitWindowResult.calculatedBounds
            minRight = fitWindowResult.minRight
            maxRight = fitWindowResult.maxRight

            if (heightFixed) {
                if (!allWindowsFit) {
                    // Revert the previous change to [rightBound] and do one last pass.
                    rightBound++
                    makeLastAdjustment = true
                    break
                }
                // Break if all the windows are zero-width at the current scale.
                if (maxRight <= availableLayoutBounds.left) {
                    break
                }
            } else {
                // Find the optimal row height bisecting between [lowHeight] and [highHeight].
                if (allWindowsFit) {
                    lowHeight = optimalHeight.toInt()
                } else {
                    highHeight = optimalHeight.toInt()
                }
                optimalHeight = 0.5f * (lowHeight + highHeight)
                // When height can no longer be improved, start balancing the rows.
                if (optimalHeight.toInt() == lowHeight) {
                    heightFixed = true
                }
            }

            if (allWindowsFit && heightFixed) {
                if (maxRight - minRight <= widthDiff) {
                    // Row alignment is getting better. Try to shrink the [rightBound] in order to
                    // squeeze the widest row.
                    rightBound = maxRight - 1
                    widthDiff = maxRight - minRight
                } else {
                    // Row alignment is getting worse.
                    // Revert the previous change to [rightBound] and do one last pass.
                    rightBound++
                    makeLastAdjustment = true
                    break
                }
            }
        }

        // Once the windows no longer fit, the change to [rightBound] was reverted. Perform one last
        // pass to position the [resultRects].
        if (makeLastAdjustment) {
            val fitWindowResult =
                fitWindowRectsInBounds(
                    Rect(availableLayoutBounds).apply { right = rightBound },
                    validTaskBounds,
                    minOf(
                        DesktopLayoutUtils.getMaxTaskHeight(availableLayoutBounds),
                        optimalHeight.toInt(),
                    ),
                    layoutConfig,
                )
            resultRects = fitWindowResult.calculatedBounds
        }

        return resultRects
    }

    /**
     * Data structure to hold the returned result of [fitWindowRectsInBounds] function.
     * [allWindowsFit] specifies whether all windows can be fit into the provided layout bounds.
     * [calculatedBounds] specifies the output bounds for all provided task windows. [minRight]
     * specifies the right bound of the narrowest row. [maxRight] specifies the right bound of the
     * widest rows.
     */
    data class FitWindowResult(
        val allWindowsFit: Boolean,
        val calculatedBounds: List<RectF>,
        val minRight: Int,
        val maxRight: Int,
    )

    /**
     * Attempts to fit all [taskBounds] inside [layoutBounds]. The method ensures that the returned
     * output bounds list has appropriate size and populates it with the values placing task windows
     * next to each other left-to-right in rows of equal [optimalWindowHeight].
     */
    private fun fitWindowRectsInBounds(
        layoutBounds: Rect,
        taskBounds: List<DesktopTaskBoundsData>,
        optimalWindowHeight: Int,
        layoutConfig: DesktopLayoutConfig,
    ): FitWindowResult {
        val numTasks = taskBounds.size
        val outRects = MutableList(numTasks) { RectF() }

        val verticalPadding = layoutConfig.verticalPaddingBetweenTasks
        val horizontalPadding = layoutConfig.horizontalPaddingBetweenTasks

        // Start in the top-left corner of [layoutBounds].
        var left = layoutBounds.left
        var top = layoutBounds.top

        // Right bound of the narrowest row.
        var minRight = layoutBounds.right
        // Right bound of the widest row.
        var maxRight = layoutBounds.left

        var allWindowsFit = true
        for (i in 0 until numTasks) {
            val taskBounds = taskBounds[i].bounds

            // Use the height to calculate the width
            val scale = optimalWindowHeight / taskBounds.height().toFloat()
            val width = (taskBounds.width() * scale).toInt()
            val optimalRowHeight = optimalWindowHeight + verticalPadding

            if (left + width + horizontalPadding > layoutBounds.right) {
                // Move to the next row if possible.
                minRight = minOf(minRight, left)
                maxRight = maxOf(maxRight, left)
                top += optimalRowHeight

                // Check if the new row reaches the bottom or if the first item in the new
                // row does not fit within the available width.
                if (
                    (top + optimalRowHeight) > layoutBounds.bottom ||
                        layoutBounds.left + width + horizontalPadding > layoutBounds.right
                ) {
                    allWindowsFit = false
                    break
                }
                left = layoutBounds.left
            }

            // Position the current rect.
            outRects[i] =
                RectF(
                    left.toFloat(),
                    top.toFloat(),
                    (left + width).toFloat(),
                    (top + optimalWindowHeight).toFloat(),
                )

            // Increment horizontal position.
            left += (width + horizontalPadding)
        }

        // Update the narrowest and widest row width for the last row.
        minRight = minOf(minRight, left)
        maxRight = maxOf(maxRight, left)

        return FitWindowResult(allWindowsFit, outRects, minRight, maxRight)
    }

    /** Centers task windows in the center of Overview. */
    private fun centerTaskWindows(
        layoutBounds: Rect,
        maxBottom: Int,
        outWindowRects: List<RectF>,
        layoutConfig: DesktopLayoutConfig,
    ) {
        if (outWindowRects.isEmpty()) {
            return
        }

        val currentRowUnionRange = RectF(outWindowRects[0])
        var currentRowY = outWindowRects[0].top
        var currentRowFirstItemIndex = 0
        val offsetY = (layoutBounds.bottom - maxBottom) / 2f
        val horizontal_padding =
            if (outWindowRects.size == 1) 0 else layoutConfig.horizontalPaddingBetweenTasks

        // Batch process to center overview desktop task windows within the same row.
        fun batchCenterDesktopTaskWindows(endIndex: Int) {
            // Calculate the shift amount required to center the desktop task items.
            val rangeCenterX =
                (currentRowUnionRange.left + currentRowUnionRange.right + horizontal_padding) / 2f
            val currentDiffX = (layoutBounds.centerX() - rangeCenterX).coerceAtLeast(0f)
            for (j in currentRowFirstItemIndex until endIndex) {
                outWindowRects[j].offset(currentDiffX, offsetY)
            }
        }

        outWindowRects.forEachIndexed { index, rect ->
            if (rect.top != currentRowY) {
                // As a new row begins processing, batch-shift the previous row's rects
                // and reset its parameters.
                batchCenterDesktopTaskWindows(index)
                currentRowUnionRange.set(rect)
                currentRowY = rect.top
                currentRowFirstItemIndex = index
            }

            // Extend the range by adding the [rect]'s width and extra in-between items
            // spacing.
            currentRowUnionRange.right = rect.right
        }

        // Post-processing rects in the last row.
        batchCenterDesktopTaskWindows(outWindowRects.size)
    }

    /** Returns true if all task windows can fit in one row. */
    private fun canFitInOneRow(resultRect: List<RectF>): Boolean {
        if (resultRect.isEmpty()) {
            return true
        }

        val firstTop = resultRect.first().top
        return resultRect.all { it.top == firstTop }
    }
}
