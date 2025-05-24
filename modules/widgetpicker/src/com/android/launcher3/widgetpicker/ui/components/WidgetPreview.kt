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

package com.android.launcher3.widgetpicker.ui.components

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetSizeInfo

/** Renders a different types of preview for an appwidget. */
@Composable
fun WidgetPreview(
    sizeInfo: WidgetSizeInfo,
    preview: WidgetPreview,
    appwidgetInfo: AppWidgetProviderInfo,
    modifier: Modifier = Modifier,
) {
    val widgetRadius = dimensionResource(android.R.dimen.system_app_widget_background_radius)

    val density = LocalDensity.current
    val containerSize =
        with(density) {
            DpSize(sizeInfo.containerWidthPx.toDp(), sizeInfo.containerHeightPx.toDp())
        }

    Box(modifier = modifier.wrapContentSize()) {
        when (preview) {
            is WidgetPreview.PlaceholderWidgetPreview ->
                PlaceholderWidgetPreview(size = containerSize, widgetRadius = widgetRadius)

            is WidgetPreview.BitmapWidgetPreview ->
                BitmapWidgetPreview(
                    bitmap = preview.bitmap,
                    size = containerSize,
                    widgetRadius = widgetRadius,
                )

            is WidgetPreview.RemoteViewsWidgetPreview ->
                RemoteViewsWidgetPreview(
                    remoteViews = preview.remoteViews,
                    widgetInfo = appwidgetInfo,
                    sizeInfo = sizeInfo,
                    widgetRadius = widgetRadius,
                )

            is WidgetPreview.ProviderInfoWidgetPreview ->
                RemoteViewsWidgetPreview(
                    previewLayoutProviderInfo = preview.providerInfo,
                    widgetInfo = appwidgetInfo,
                    sizeInfo = sizeInfo,
                    widgetRadius = widgetRadius,
                )
        }
    }
}

@Composable
private fun PlaceholderWidgetPreview(size: DpSize, widgetRadius: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.width(size.width)
                .height(size.height)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(widgetRadius),
                ),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BitmapWidgetPreview(bitmap: Bitmap, size: DpSize, widgetRadius: Dp) {
    val density = LocalDensity.current

    val imageScale by
        remember(bitmap) {
            derivedStateOf {
                with(density) {
                    val bitmapHeight = bitmap.height.toDp()
                    val bitmapWidth = bitmap.width.toDp()
                    val bitmapAspectRatio = bitmapWidth / bitmapHeight
                    val containerAspectRatio: Float = size.width / size.height

                    // Scale by width if image has larger aspect ratio than the container else by
                    // height; and avoid cropping the previews.
                    if (bitmapAspectRatio > containerAspectRatio) {
                        size.width / bitmapWidth
                    } else {
                        size.height / bitmapHeight
                    }
                }
            }
        }
    val imageSize by
        remember(imageScale) {
            derivedStateOf {
                with(density) {
                    val bitmapHeight = bitmap.height.toDp()
                    val bitmapWidth = bitmap.width.toDp()
                    DpSize(bitmapWidth * imageScale, bitmapHeight * imageScale)
                }
            }
        }
    val scaledCornerRadius by
        remember(imageScale) {
            derivedStateOf { (widgetRadius * imageScale).coerceAtMost(widgetRadius) }
        }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null, // only visual (widget details provides the readable info)
        contentScale = ContentScale.FillBounds,
        modifier =
            Modifier.width(imageSize.width)
                .height(imageSize.height)
                .clip(shape = RoundedCornerShape(scaledCornerRadius)),
    )
}

@Composable
private fun RemoteViewsWidgetPreview(
    remoteViews: RemoteViews? = null,
    previewLayoutProviderInfo: AppWidgetProviderInfo? = null,
    widgetInfo: AppWidgetProviderInfo,
    sizeInfo: WidgetSizeInfo,
    widgetRadius: Dp,
) {
    val context = LocalContext.current
    val appWidgetHostView by
        remember(sizeInfo) {
            derivedStateOf {
                WidgetPreviewHostView(context).apply {
                    setContainerSizePx(
                        IntSize(sizeInfo.containerWidthPx, sizeInfo.containerHeightPx)
                    )
                }
            }
        }

    key(sizeInfo) {
        AndroidView(
            modifier = Modifier.wrapContentSize().clip(RoundedCornerShape(widgetRadius)),
            factory = { appWidgetHostView },
            update = { view ->
                // if preview.remoteViews is null, initial layout will render.
                // the databasePreviewLoader overwrites the initial layout in "preview.providerInfo"
                // to be the previewLayout.
                view.setAppWidget(
                    /*appWidgetId=*/ NO_OP_APP_WIDGET_ID,
                    /*info=*/ previewLayoutProviderInfo ?: widgetInfo,
                )
                view.updateAppWidget(remoteViews)
            },
            onReset = {}, // enable reuse ("update" sets the and preview info)
        )
    }
}

// We don't care about appWidgetId since this is a preview.
private const val NO_OP_APP_WIDGET_ID = -1
