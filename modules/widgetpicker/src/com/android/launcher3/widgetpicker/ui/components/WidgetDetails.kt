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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.PickableWidget

/**
 * Displays the details of the widget that can be shown below their previews.
 *
 * @param widget the information about the widget that can be used to display the details
 * @param appIcon an optional app icon that can be displayed when widget is shown outside of the
 *   app's context e.g. in recommendations.
 * @param showAllDetails when set, besides the widget label, also shows widget spans and 1-3 line
 *   long description
 * @param modifier modifier for the top level composable.
 */
@Composable
fun WidgetDetails(
    widget: PickableWidget,
    appIcon: (@Composable () -> Unit)?,
    showAllDetails: Boolean,
    modifier: Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier.fillMaxSize().padding(
            horizontal = WidgetDetailsDimension.horizontalPadding,
            vertical = WidgetDetailsDimension.verticalPadding
        ),
    ) {
        WidgetLabel(label = widget.label, appIcon = appIcon, modifier = Modifier)
        if (showAllDetails) {
            WidgetSpanSizeLabel(spanX = widget.sizeInfo.spanX, spanY = widget.sizeInfo.spanY)
            widget.description?.let { WidgetDescription(it) }
        }
    }
}

/** The label / short title of the widget provided by the developer in the manifest. */
@Composable
private fun WidgetLabel(label: String, appIcon: (@Composable () -> Unit)?, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        if (appIcon != null) {
            appIcon()
            Spacer(
                modifier =
                    Modifier.width(WidgetDetailsDimension.appIconLabelSpacing).fillMaxHeight()
            )
        }
        Text(
            text = label,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

/**
 * Display a long description provided by the developers for the widget in their appwidget provider
 * info.
 */
@Composable
private fun WidgetDescription(description: CharSequence) {
    Text(
        text = description.toString(),
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        maxLines = 3,
        style =
            MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            ),
    )
}

/** Span (X and Y) sizing info for the widget. */
@Composable
private fun WidgetSpanSizeLabel(spanX: Int, spanY: Int) {
    val contentDescription =
        stringResource(R.string.widget_span_dimensions_accessible_format, spanX, spanY)

    Text(
        text = stringResource(R.string.widget_span_dimensions_format, spanX, spanY),
        textAlign = TextAlign.Center,
        maxLines = 1,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
            ),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}

private object WidgetDetailsDimension {
    val horizontalPadding: Dp = 4.dp
    val verticalPadding: Dp = 12.dp
    val appIconLabelSpacing = 8.dp
}
