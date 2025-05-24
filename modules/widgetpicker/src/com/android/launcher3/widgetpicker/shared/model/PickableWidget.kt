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

package com.android.launcher3.widgetpicker.shared.model

import android.appwidget.AppWidgetProviderInfo

/**
 * Raw information about a widget that can be considered for display in widget picker list.
 *
 * Note: It is widget picker's responsibility to run eligibility checks to see if the widget can be
 * displayed in picker.
 *
 * @property id a unique identifier for the widget
 * @property appId a unique identifier for the app group that this widget could belong to
 * @property label a user friendly label for the widget.
 * @property description a user friendly description for the widget
 * @property appWidgetProviderInfo widget info associated with the widget as configured by the
 *   developer; note: this should be a local clone and not the object that was received from
 *   appwidget manager.
 */
data class PickableWidget(
    val id: WidgetId,
    val appId: WidgetAppId,
    val label: String,
    val description: CharSequence?,
    val appWidgetProviderInfo: AppWidgetProviderInfo,
    val sizeInfo: WidgetSizeInfo,
) {
    // Custom toString to account for the appWidgetProviderInfo.
    override fun toString(): String =
        "PickableWidget(id=$id,appId=$appId,label=$label,description=$description," +
            "sizeInfo=$sizeInfo,provider=${appWidgetProviderInfo.provider})"
}

/**
 * Sizing information for a specific widget shown in a grid.
 *
 * @param spanX the number of horizontal cells in the host's grid that this widget takes
 * @param spanY the number of vertical cells in the host's grid that this widget takes
 * @param widthPx the width in pixels that the widget should ideally be sized at based on host's
 *   grid
 * @param heightPx the height in pixels that the widget should ideally be sized at based on host's
 *   grid
 * @param containerWidthPx the width of container in which the widget may need to be fit to; For
 *   instance, for visual coherence, widgets of sizes like 3x2 are shown in 2x2 container based on a
 *   predefined mapping logic. This allows us to show them in a single row when space permits.
 *   [containerWidthPx] is the width in pixel for such a container. [containerHeightPx] is the
 *   height in pixels for the container spans.
 */
data class WidgetSizeInfo(
    val spanX: Int,
    val spanY: Int,
    val widthPx: Int,
    val heightPx: Int,
    val containerWidthPx: Int,
    val containerHeightPx: Int,
)
