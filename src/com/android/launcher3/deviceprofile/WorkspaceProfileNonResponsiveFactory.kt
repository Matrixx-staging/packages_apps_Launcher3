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

package com.android.launcher3.deviceprofile

import android.graphics.Point
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getIconSizeWithOverlap
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import kotlin.math.max
import kotlin.math.min

@Deprecated(
    "Non responsive grids are deprecated, please refer to responsive grid for a " +
        "replacement. Nexus Launcher doesn't use Non Scalable grids anymore."
)
/**
 * This factory creates WorkspaceProfile when the grid is Scalable and Non-Scalable. Calculating
 * this two variants is completely different from calculating the responsive grid mainly because
 * both of them require the WorkspaceProfile to be calculated once and then recalculated again if
 * the scaleY is less than 1.
 *
 * For reference please look at {@code WorkspaceProfile#Factory}.
 */
object WorkspaceProfileNonResponsiveFactory {

    fun hideWorkspaceLabelsIfNotEnoughSpace(
        cellSize: Point,
        isVerticalLayout: Boolean,
        workspaceProfile: WorkspaceProfile,
    ): WorkspaceProfile {
        if (!isVerticalLayout) return workspaceProfile

        val iconTextHeight =
            Utilities.calculateTextHeight(workspaceProfile.iconTextSizePx.toFloat()).toFloat()
        val workspaceCellPaddingY: Float =
            (cellSize.y -
                workspaceProfile.iconSizePx -
                workspaceProfile.iconDrawablePaddingPx -
                iconTextHeight)

        if (workspaceCellPaddingY >= iconTextHeight) return workspaceProfile

        // We want enough space so that the text is closer to its corresponding icon.
        return workspaceProfile.copy(
            iconTextSizePx = 0,
            iconDrawablePaddingPx = 0,
            cellHeightPx = getIconSizeWithOverlap(workspaceProfile.iconSizePx),
            maxIconTextLineCount = 0,
            isLabelHidden = true,
        )
    }

    fun createWorkspaceProfileNonScalable(
        deviceProperties: DeviceProperties,
        cellScaleToFit: Float,
        iconScale: Float,
        iconSizePx: Int,
        iconTextSizePx: Int,
        isVerticalLayout: Boolean,
        cellSize: Point,
        iconDrawablePaddingOriginalPx: Int,
        cellLayoutBorderSpacePx: Point,
        desiredWorkspaceHorizontalMarginOriginalPx: Int,
    ): WorkspaceProfile {
        var iconDrawablePaddingPx =
            (getNormalizedIconDrawablePadding(iconSizePx, iconDrawablePaddingOriginalPx) *
                    iconScale)
                .toInt()
        val cellWidthPx = iconSizePx + iconDrawablePaddingPx
        var cellHeightPx =
            (getIconSizeWithOverlap(iconSizePx) +
                iconDrawablePaddingPx +
                Utilities.calculateTextHeight(iconTextSizePx.toFloat()))
        val cellPaddingY: Int = (cellSize.y - cellHeightPx) / 2
        if (
            iconDrawablePaddingPx > cellPaddingY &&
                !isVerticalLayout &&
                !deviceProperties.isExternalDisplay
        ) {
            // Ensures that the label is closer to its corresponding icon. This is not an issue
            // with vertical bar layout or external display mode since the issue is handled
            // separately with their calls to {@link #adjustToHideWorkspaceLabels}.
            cellHeightPx -= (iconDrawablePaddingPx - cellPaddingY)
            iconDrawablePaddingPx = cellPaddingY
        }

        return WorkspaceProfile(
            // Workspace icons
            iconScale = iconScale,
            iconSizePx = iconSizePx,
            iconTextSizePx = iconTextSizePx,
            iconDrawablePaddingPx = iconDrawablePaddingPx,
            cellScaleToFit = cellScaleToFit,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
            desiredWorkspaceHorizontalMarginPx = desiredWorkspaceHorizontalMarginOriginalPx,
            cellYPaddingPx = -1,
            maxIconTextLineCount = 1,
            iconCenterVertically = false,
        )
    }

    fun createWorkspaceProfileScalable(
        scale: Float,
        inv: InvariantDeviceProfile,
        typeIndex: Int,
        mMetrics: DisplayMetrics,
        iconSizePxParam: Int,
        iconTextSizePxParam: Int,
        iconScale: Float,
        cellLayoutBorderSpacePx: Point,
        mIconDrawablePaddingOriginalPx: Int,
        cellScaleToFit: Float,
        desiredWorkspaceHorizontalMarginOriginalPx: Int,
        panelCount: Int,
        isVerticalLayout: Boolean,
    ): WorkspaceProfile {
        var iconTextSizePx = iconTextSizePxParam
        var iconSizePx = iconSizePxParam
        var iconDrawablePaddingPx =
            (getNormalizedIconDrawablePadding(iconSizePx, mIconDrawablePaddingOriginalPx) *
                    iconScale)
                .toInt()
        var cellWidthPx = pxFromDp(inv.minCellSize.get(typeIndex).x, mMetrics, scale)
        var cellHeightPx = pxFromDp(inv.minCellSize.get(typeIndex).y, mMetrics, scale)

        if (cellWidthPx < iconSizePx) {
            // If cellWidth no longer fit iconSize, reduce borderSpace to make cellWidth bigger.
            val numColumns: Int = panelCount * inv.numColumns
            val numBorders = numColumns - 1
            val extraWidthRequired: Int = (iconSizePx - cellWidthPx) * numColumns
            if (cellLayoutBorderSpacePx.x * numBorders >= extraWidthRequired) {
                cellWidthPx = iconSizePx
                cellLayoutBorderSpacePx.x -= extraWidthRequired / numBorders
            } else {
                // If it still doesn't fit, set borderSpace to 0 and distribute the space for
                // cellWidth, and reduce iconSize.
                cellWidthPx =
                    (cellWidthPx * numColumns + cellLayoutBorderSpacePx.x * numBorders) / numColumns
                iconSizePx = min(iconSizePx.toDouble(), cellWidthPx.toDouble()).toInt()
                cellLayoutBorderSpacePx.x = 0
            }
        }

        var cellTextAndPaddingHeight: Int =
            iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx.toFloat())
        var cellContentHeight: Int = iconSizePx + cellTextAndPaddingHeight
        if (cellHeightPx < cellContentHeight) {
            // If cellHeight no longer fit iconSize, reduce borderSpace to make cellHeight
            // bigger.
            val numBorders: Int = inv.numRows - 1
            val extraHeightRequired: Int = (cellContentHeight - cellHeightPx) * inv.numRows
            if (cellLayoutBorderSpacePx.y * numBorders >= extraHeightRequired) {
                cellHeightPx = cellContentHeight
                cellLayoutBorderSpacePx.y -= extraHeightRequired / numBorders
            } else {
                // If it still doesn't fit, set borderSpace to 0 to recover space.
                cellHeightPx =
                    (cellHeightPx * inv.numRows + cellLayoutBorderSpacePx.y * numBorders) /
                        inv.numRows
                cellLayoutBorderSpacePx.y = 0
                // Reduce iconDrawablePaddingPx to make cellContentHeight smaller.
                val cellContentWithoutPadding: Int = cellContentHeight - iconDrawablePaddingPx
                if (cellContentWithoutPadding <= cellHeightPx) {
                    iconDrawablePaddingPx = cellContentHeight - cellHeightPx
                } else {
                    // If it still doesn't fit, set iconDrawablePaddingPx to 0 to recover space,
                    // then proportional reduce iconSizePx and iconTextSizePx to fit.
                    iconDrawablePaddingPx = 0
                    val ratio: Float = cellHeightPx / cellContentWithoutPadding.toFloat()
                    iconSizePx = (iconSizePx * ratio).toInt()
                    iconTextSizePx = (iconTextSizePx * ratio).toInt()
                }
                cellTextAndPaddingHeight =
                    iconDrawablePaddingPx + Utilities.calculateTextHeight(iconTextSizePx.toFloat())
            }
            cellContentHeight = iconSizePx + cellTextAndPaddingHeight
        }
        return WorkspaceProfile(
            // Workspace icons
            iconScale = iconScale,
            iconSizePx = iconSizePx,
            iconTextSizePx = iconTextSizePx,
            iconDrawablePaddingPx = iconDrawablePaddingPx,
            cellScaleToFit = cellScaleToFit,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
            desiredWorkspaceHorizontalMarginPx =
                (desiredWorkspaceHorizontalMarginOriginalPx * scale).toInt(),
            cellYPaddingPx = max(0, (cellHeightPx - cellContentHeight)) / 2,
            maxIconTextLineCount = 1,
            iconCenterVertically = isVerticalLayout,
        )
    }

    fun createWorkspaceProfileNonResponsive(
        deviceProperties: DeviceProperties,
        scale: Float,
        inv: InvariantDeviceProfile,
        isVerticalLayout: Boolean,
        isScalableGrid: Boolean,
        cellSize: Point,
        iconDrawablePaddingOriginalPx: Int,
        typeIndex: Int,
        metrics: DisplayMetrics,
        panelCount: Int,
        desiredWorkspaceHorizontalMarginOriginalPx: Int,
        cellLayoutBorderSpacePx: Point,
        iconSizePx: Int,
    ): WorkspaceProfile {
        // Icon scale should never exceed 1, otherwise pixellation may occur.
        val iconScale = min(1f, scale)
        val cellScaleToFit = scale
        val iconTextSizePx = pxFromSp(inv.iconTextSize[typeIndex], metrics)
        // Workspace
        return when {
            isScalableGrid ->
                createWorkspaceProfileScalable(
                        scale = scale,
                        inv = inv,
                        typeIndex = typeIndex,
                        mMetrics = metrics,
                        iconSizePxParam = iconSizePx,
                        iconTextSizePxParam = iconTextSizePx,
                        iconScale = iconScale,
                        cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                        mIconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
                        cellScaleToFit = cellScaleToFit,
                        desiredWorkspaceHorizontalMarginOriginalPx =
                            desiredWorkspaceHorizontalMarginOriginalPx,
                        panelCount = panelCount,
                        isVerticalLayout = isVerticalLayout,
                    )
                    .let { hideWorkspaceLabelsIfNotEnoughSpace(cellSize, isVerticalLayout, it) }

            else ->
                createWorkspaceProfileNonScalable(
                        deviceProperties = deviceProperties,
                        cellScaleToFit = cellScaleToFit,
                        iconScale = iconScale,
                        iconSizePx = iconSizePx,
                        iconTextSizePx = iconTextSizePx,
                        isVerticalLayout = isVerticalLayout,
                        cellSize = cellSize,
                        iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
                        cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                        desiredWorkspaceHorizontalMarginOriginalPx =
                            desiredWorkspaceHorizontalMarginOriginalPx,
                    )
                    .let { hideWorkspaceLabelsIfNotEnoughSpace(cellSize, isVerticalLayout, it) }
        }
    }
}
