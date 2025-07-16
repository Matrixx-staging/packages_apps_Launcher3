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

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.util.DisplayMetrics
import com.android.launcher3.DevicePaddings
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities.getIconSizeWithOverlap
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.deviceprofile.WorkspaceProfileNonResponsiveFactory.createWorkspaceProfileNonResponsive
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils
import com.android.launcher3.util.CellContentDimensions
import com.android.launcher3.util.IconSizeSteps
import kotlin.math.max
import kotlin.math.min

/**
 * All the variables that visually define the Workspace and are dependant on the device
 * configuration.
 */
data class WorkspaceProfile(
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
    val desiredWorkspaceHorizontalMarginOriginalPx: Int,
    val workspaceContentScale: Float,
    val workspaceSpringLoadedMinNextPageVisiblePx: Int,
    val maxEmptySpace: Int,
    val workspaceTopPadding: Int,
    val workspaceBottomPadding: Int,
    val workspaceCellPaddingXPx: Int,

    // Visualization
    val gridVisualizationPaddingX: Int,
    val gridVisualizationPaddingY: Int,

    // Workspace page indicator
    val workspacePageIndicatorHeight: Int,
    val workspacePageIndicatorOverlapWorkspace: Int,
    val isLabelHidden: Boolean = false,
    val iconDrawablePaddingOriginalPx: Int,
) {

    // TODO(b/432070502)
    @Deprecated(
        "This is only used for scalable which is deprecated. This should also go away once " +
            "we add extraSpace into the WorkspaceProfile"
    )
    fun calculateAndSetWorkspaceVerticalPadding(
        context: Context,
        inv: InvariantDeviceProfile,
        extraSpace: Int,
    ): WorkspaceProfile {
        if (inv.devicePaddingId != ResourceUtils.INVALID_RESOURCE_HANDLE) {
            // Paddings were created assuming no scaling, so we first unscale the extra space.
            val unscaledExtraSpace: Int = (extraSpace / cellScaleToFit).toInt()
            val devicePaddings = DevicePaddings(context, inv.devicePaddingId)
            val padding = devicePaddings.getDevicePadding(unscaledExtraSpace)
            return copy(
                maxEmptySpace = padding.maxEmptySpacePx,
                workspaceTopPadding =
                    Math.round(padding.getWorkspaceTopPadding(unscaledExtraSpace) * cellScaleToFit),
                workspaceBottomPadding =
                    Math.round(
                        padding.getWorkspaceBottomPadding(unscaledExtraSpace) * cellScaleToFit
                    ),
            )
        }
        return this
    }

    // TODO(b/430382569)
    @Deprecated(
        "This classes should be treated as immutable, in order to change it we" +
            "should use a factory and create a new one."
    )
    fun changeIconSize(iconSizePx: Int): WorkspaceProfile {
        return copy(iconSizePx = iconSizePx)
    }

    companion object Factory {

        fun createWorkspaceProfileResponsiveGrid(
            res: Resources,
            iconSizeSteps: IconSizeSteps,
            isVerticalLayout: Boolean,
            responsiveWorkspaceWidthSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceHeightSpec: CalculatedResponsiveSpec,
            responsiveWorkspaceCellSpec: CalculatedCellSpec,
            iconScale: Float,
            cellLayoutBorderSpacePx: Point,
            cellSize: Point,
            cellScaleToFit: Float,
        ): WorkspaceProfile {
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
                desiredWorkspaceHorizontalMarginPx = responsiveWorkspaceWidthSpec.startPaddingPx,
                cellYPaddingPx = cellYPaddingPx,
                maxIconTextLineCount = maxIconTextLineCount,
                iconCenterVertically = isVerticalLayout,
                gridVisualizationPaddingX =
                    res.getDimensionPixelSize(R.dimen.grid_visualization_horizontal_cell_spacing),
                gridVisualizationPaddingY =
                    res.getDimensionPixelSize(R.dimen.grid_visualization_vertical_cell_spacing),
                workspacePageIndicatorHeight =
                    res.getDimensionPixelSize(R.dimen.workspace_page_indicator_height),
                workspacePageIndicatorOverlapWorkspace =
                    res.getDimensionPixelSize(R.dimen.workspace_page_indicator_overlap_workspace),
                iconDrawablePaddingOriginalPx = responsiveWorkspaceCellSpec.iconDrawablePadding,
                desiredWorkspaceHorizontalMarginOriginalPx =
                    responsiveWorkspaceWidthSpec.startPaddingPx,
                workspaceContentScale = res.getFloat(R.dimen.workspace_content_scale),
                workspaceSpringLoadedMinNextPageVisiblePx =
                    res.getDimensionPixelSize(
                        R.dimen.dynamic_grid_spring_loaded_min_next_space_visible
                    ),
                workspaceCellPaddingXPx =
                    res.getDimensionPixelSize(R.dimen.dynamic_grid_cell_padding_x),
                workspaceTopPadding = responsiveWorkspaceHeightSpec.startPaddingPx,
                workspaceBottomPadding = responsiveWorkspaceHeightSpec.endPaddingPx,
                maxEmptySpace = 0,
            )
        }

        fun createWorkspaceProfile(
            context: Context,
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
            typeIndex: Int,
            metrics: DisplayMetrics,
            panelCount: Int,
            cellLayoutBorderSpacePx: Point,
            iconSizePx: Int,
        ): WorkspaceProfile {
            // Icon scale should never exceed 1, otherwise pixellation may occur.
            val iconScale = min(1f, scale)
            val cellScaleToFit = scale
            // Workspace
            return when {
                (isResponsiveGrid &&
                    mResponsiveWorkspaceWidthSpec != null &&
                    mResponsiveWorkspaceHeightSpec != null &&
                    mResponsiveWorkspaceCellSpec != null) ->
                    createWorkspaceProfileResponsiveGrid(
                        res = res,
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

                else ->
                    createWorkspaceProfileNonResponsive(
                        res = res,
                        deviceProperties = deviceProperties,
                        scale = scale,
                        inv = inv,
                        isVerticalLayout = isVerticalLayout,
                        isScalableGrid = isScalableGrid,
                        cellSize = cellSize,
                        typeIndex = typeIndex,
                        metrics = metrics,
                        panelCount = panelCount,
                        cellLayoutBorderSpacePx = cellLayoutBorderSpacePx,
                        iconSizePx = iconSizePx,
                        context = context,
                    )
            }
        }
    }
}
