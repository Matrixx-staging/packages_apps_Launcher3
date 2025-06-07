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

package com.android.launcher3.widgetpicker.listeners

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.view.View
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY
import com.android.launcher3.dragndrop.BaseItemDragListener
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader.WidgetPreviewInfo
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.PendingItemDragHelper
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview

/**
 * A callback listener of type [BaseItemDragListener] that handles widget drag and drop from widget
 * picker hosted in a separate activity than home screen.
 *
 * Responsible for initializing the [PendingItemDragHelper] that then handles the rest of the
 * drag and drop (including showing a drag shadow for the widget).
 *
 * @param mimeType a mime type used by widget picker when attaching this listener for a specific
 * widget's drag and drop session.
 * @param appWidgetProviderInfo provider info of the widget being dragged
 * @param previewRect the bounds of widget's preview offset by the point of long press
 * @param previewWidth width of the preview as it appears in the widget picker.
 */
class WidgetPickerDragItemListener(
    private val mimeType: String,
    private val appWidgetProviderInfo: AppWidgetProviderInfo,
    private val widgetPreview: WidgetPreview,
    previewRect: Rect,
    previewWidth: Int
) : BaseItemDragListener(previewRect, previewWidth, previewWidth) {
    override fun getMimeType(): String = mimeType

    override fun createDragHelper(): PendingItemDragHelper {
        val launcherProviderInfo =
            LauncherAppWidgetProviderInfo.fromProviderInfo(mLauncher, appWidgetProviderInfo)
        val pendingAddWidgetInfo =
            PendingAddWidgetInfo(launcherProviderInfo, CONTAINER_WIDGETS_TRAY)

        val view = View(mLauncher)
        view.tag = pendingAddWidgetInfo

        val dragHelper = PendingItemDragHelper(view)

        val info = WidgetPreviewInfo()
        when (widgetPreview) {
            is WidgetPreview.BitmapWidgetPreview -> {
                info.previewBitmap = widgetPreview.bitmap
                info.providerInfo = appWidgetProviderInfo
            }

            is WidgetPreview.ProviderInfoWidgetPreview -> {
                info.providerInfo = widgetPreview.providerInfo
            }

            is WidgetPreview.RemoteViewsWidgetPreview -> {
                info.remoteViews = widgetPreview.remoteViews
                info.providerInfo = appWidgetProviderInfo
            }

            else -> throw IllegalStateException(
                "Unsupported preview type when dropping widget to launcher"
            )
        }
        dragHelper.setWidgetPreviewInfo(info)

        return dragHelper
    }
}
