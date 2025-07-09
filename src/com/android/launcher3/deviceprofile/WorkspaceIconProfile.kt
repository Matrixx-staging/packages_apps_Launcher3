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

import android.content.res.Resources
import android.graphics.Point
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getIconSizeWithOverlap
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import com.android.launcher3.util.CellContentDimensions
import com.android.launcher3.util.IconSizeSteps
import kotlin.math.max
import kotlin.math.min

data class WorkspaceIconProfile(
    // Workspace icons
    val iconScale: Float,
    val iconSizePx: Int,
    val iconTextSizePx: Int,
    val iconDrawablePaddingPx: Int,
    val cellScaleToFit: Float,
    val cellWidthPx: Int,
    val cellHeightPx: Int,
    val cellLayoutBorderSpacePx: Point,
    val desiredWorkspaceHorizontalMarginPx: Int,
    val cellYPaddingPx: Int = -1,
    val maxIconTextLineCount: Int,
    val iconCenterVertically: Boolean,
    val isLabelHidden: Boolean = false,
) {

    // TODO(b/430382569)
    @Deprecated(
        "This classes should be treated as immutable, in order to change it we" +
            "should use a factory and create a new one."
    )
    fun changeIconSize(iconSizePx: Int): WorkspaceIconProfile {
        return copy(iconSizePx = iconSizePx)
    }

    companion object Factory {

        private fun hideWorkspaceLabelsIfNotEnoughSpace(
            cellSize: Point,
            isVerticalLayout: Boolean,
            workspaceIconProfile: WorkspaceIconProfile,
        ): WorkspaceIconProfile {
            if (!isVerticalLayout) return workspaceIconProfile

            val iconTextHeight =
                Utilities.calculateTextHeight(workspaceIconProfile.iconTextSizePx.toFloat())
                    .toFloat()
            val workspaceCellPaddingY: Float =
                (cellSize.y -
                    workspaceIconProfile.iconSizePx -
                    workspaceIconProfile.iconDrawablePaddingPx -
                    iconTextHeight)

            if (workspaceCellPaddingY >= iconTextHeight) return workspaceIconProfile

            // We want enough space so that the text is closer to its corresponding icon.
            return workspaceIconProfile.copy(
                iconTextSizePx = 0,
                iconDrawablePaddingPx = 0,
                cellHeightPx = getIconSizeWithOverlap(workspaceIconProfile.iconSizePx),
                maxIconTextLineCount = 0,
                isLabelHidden = true,
            )
        }

        fun createWorkspaceIconProfileNonScalable(
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
        ): WorkspaceIconProfile {
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

            return WorkspaceIconProfile(
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

        fun createWorkspaceIconProfileScalable(
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
        ): WorkspaceIconProfile {
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
                        (cellWidthPx * numColumns + cellLayoutBorderSpacePx.x * numBorders) /
                            numColumns
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
                        iconDrawablePaddingPx +
                            Utilities.calculateTextHeight(iconTextSizePx.toFloat())
                }
                cellContentHeight = iconSizePx + cellTextAndPaddingHeight
            }
            return WorkspaceIconProfile(
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

        fun createWorkspaceIconProfileResponsiveGrid(
            iconSizeSteps: IconSizeSteps,
            isVerticalLayout: Boolean,
            responsiveWorkspaceWidthSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceHeightSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceCellSpec: CalculatedCellSpec,
            iconScale: Float,
            cellLayoutBorderSpacePx: Point,
            cellSize: Point,
            cellScaleToFit: Float,
        ): WorkspaceIconProfile {
            val iconDrawablePaddingOriginalPx = responsiveWorkspaceCellSpec.iconDrawablePadding
            var iconTextSizePx = responsiveWorkspaceCellSpec.iconTextSize
            var iconSizePx = responsiveWorkspaceCellSpec.iconSize
            val cellWidthPx = responsiveWorkspaceWidthSpec.cellSizePx
            val cellHeightPx = responsiveWorkspaceHeightSpec.cellSizePx
            var maxIconTextLineCount = responsiveWorkspaceCellSpec.iconTextMaxLineCount

            if (cellWidthPx < iconSizePx) {
                // get a smaller icon size
                iconSizePx = iconSizeSteps.getIconSmallerThan(cellWidthPx)
            }

            var iconDrawablePaddingPx: Int
            if (isVerticalLayout) {
                iconDrawablePaddingPx = 0
                iconTextSizePx = 0
                maxIconTextLineCount = 0
            } else {
                iconDrawablePaddingPx =
                    getNormalizedIconDrawablePadding(iconSizePx, iconDrawablePaddingOriginalPx)
            }

            val cellContentDimensions =
                CellContentDimensions(
                    iconSizePx,
                    iconDrawablePaddingPx,
                    iconTextSizePx,
                    maxIconTextLineCount,
                )
            val cellContentHeight =
                cellContentDimensions.resizeToFitCellHeight(cellHeightPx, iconSizeSteps)
            iconSizePx = cellContentDimensions.iconSizePx
            iconDrawablePaddingPx = cellContentDimensions.iconDrawablePaddingPx
            iconTextSizePx = cellContentDimensions.iconTextSizePx
            maxIconTextLineCount = cellContentDimensions.maxLineCount

            val cellYPaddingPx =
                if (isVerticalLayout) {
                    max(0, cellSize.y - getIconSizeWithOverlap(iconSizePx)) / 2
                } else {
                    max(0, (cellHeightPx - cellContentHeight)) / 2
                }

            return WorkspaceIconProfile(
                // Workspace icons
                iconScale = iconScale,
                iconSizePx = iconSizePx,
                iconTextSizePx = iconTextSizePx,
                iconDrawablePaddingPx = iconDrawablePaddingPx,
                cellScaleToFit = cellScaleToFit,
                cellWidthPx = cellWidthPx,
                cellHeightPx = cellHeightPx,
                cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                desiredWorkspaceHorizontalMarginPx = responsiveWorkspaceWidthSpec.startPaddingPx,
                cellYPaddingPx = cellYPaddingPx,
                maxIconTextLineCount = maxIconTextLineCount,
                iconCenterVertically = isVerticalLayout,
            )
        }

        fun createWorkspaceIconProfile(
            res: Resources,
            deviceProperties: DeviceProperties,
            scale: Float,
            inv: InvariantDeviceProfile,
            iconSizeSteps: IconSizeSteps,
            isVerticalLayout: Boolean,
            isResponsiveGrid: Boolean,
            isScalableGrid: Boolean,
            mResponsiveWorkspaceWidthSpec: CalculatedResponsiveSpec?,
            mResponsiveWorkspaceHeightSpec: CalculatedResponsiveSpec?,
            mResponsiveWorkspaceCellSpec: CalculatedCellSpec?,
            cellSize: Point,
            iconDrawablePaddingOriginalPx: Int,
            typeIndex: Int,
            metrics: DisplayMetrics,
            panelCount: Int,
            desiredWorkspaceHorizontalMarginOriginalPx: Int,
            cellLayoutBorderSpacePx: Point,
            iconSizePx: Int,
        ): WorkspaceIconProfile {
            // Icon scale should never exceed 1, otherwise pixellation may occur.
            val iconScale = min(1f, scale)
            val cellScaleToFit = scale
            val iconTextSizePx = pxFromSp(inv.iconTextSize[typeIndex], metrics)
            // Workspace
            return when {
                (isResponsiveGrid &&
                    mResponsiveWorkspaceWidthSpec != null &&
                    mResponsiveWorkspaceHeightSpec != null &&
                    mResponsiveWorkspaceCellSpec != null) ->
                    createWorkspaceIconProfileResponsiveGrid(
                        iconSizeSteps = iconSizeSteps,
                        isVerticalLayout = isVerticalLayout,
                        responsiveWorkspaceWidthSpec = mResponsiveWorkspaceWidthSpec,
                        responsiveWorkspaceHeightSpec = mResponsiveWorkspaceHeightSpec,
                        responsiveWorkspaceCellSpec = mResponsiveWorkspaceCellSpec,
                        iconScale = iconScale,
                        cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                        cellSize = cellSize,
                        cellScaleToFit = cellScaleToFit,
                    )

                isScalableGrid ->
                    createWorkspaceIconProfileScalable(
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
                    createWorkspaceIconProfileNonScalable(
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
}
