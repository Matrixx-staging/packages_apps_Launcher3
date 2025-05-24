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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R

/**
 * A list header in widget picker that when [expanded] displays the [expandedContent].
 *
 * Useful for single pane layouts where content is shown inline with the header on selection.
 *
 * @param modifier modifier for the top level composable of header
 * @param expanded whether to show the [expandedContent] below the header
 * @param leadingAppIcon an app icon shown in the beginning of the header row
 * @param title a short 1 line title for the header
 * @param subTitle a short 1 line description (e.g. number of items in the [expandedContent]).
 * @param expandedContent the content for the header when its selected
 * @param onClick action to perform on click; e.g. manage the expand / collapse state
 * @param shape shape for the header e.g. a different shape based on position in the list
 */
@Composable
fun ExpandableListHeader(
    modifier: Modifier,
    expanded: Boolean,
    leadingAppIcon: @Composable () -> Unit,
    title: String,
    subTitle: String,
    expandedContent: @Composable () -> Unit,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
) {
    val finalModifier =
        modifier
            .clip(shape = shape)
            .background(color = ExpandedListHeaderDefaults.backgroundColor)

    Column(modifier = finalModifier) {
        WidgetAppHeader(
            modifier = Modifier
                .clickable { onClick() },
            leadingIcon = { leadingAppIcon() },
            title = title,
            subTitle = subTitle,
            selected = expanded,
            trailingButton = { ExpandCollapseIndicator(expanded) },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = ExpandedListHeaderDefaults.contentExpandAnimationSpec,
            exit = ExpandedListHeaderDefaults.contentCollapseAnimationSpec,
            modifier = Modifier.fillMaxWidth(),
        ) {
            expandedContent()
        }
    }
}

/**
 * A list header in widget picker that is selectable by clicking it.
 *
 * Useful for two pane layouts where content is shown in right pane while header is shown in left.
 *
 * @param modifier modifier for the top level composable of header
 * @param selected whether to show highlight the header's background to indicate its currently
 *   selected.
 * @param leadingAppIcon an app icon shown in the beginning of the header row
 * @param title a short 1 line title for the header
 * @param subTitle a short 1 line description (e.g. number of widgets in the selected app).
 * @param onSelect action to perform when user clicks to select the header
 * @param shape shape for the header e.g. depending on position in the list, a different corner
 * @param selectedBackgroundColor background color when header is [selected]
 * @param unSelectedBackgroundColor background color when header is not [selected]
 */
@Composable
fun SelectableListHeader(
    modifier: Modifier,
    selected: Boolean,
    leadingAppIcon: @Composable () -> Unit,
    title: String,
    subTitle: String,
    onSelect: () -> Unit,
    shape: RoundedCornerShape,
    selectedBackgroundColor: Color = ClickableListHeaderDefaults.selectedBackgroundColor,
    unSelectedBackgroundColor: Color = ClickableListHeaderDefaults.unSelectedBackgroundColor,
) {
    val clickModifier =
        if (!selected) {
            Modifier.clickable { onSelect() }
        } else {
            Modifier
        }

    WidgetAppHeader(
        modifier =
            modifier
                .semantics(mergeDescendants = true) { this.selected = selected }
                .clip(shape = shape)
                .background(
                    color =
                        if (selected) {
                            selectedBackgroundColor
                        } else {
                            unSelectedBackgroundColor
                        }
                )
                .then(clickModifier),
        leadingIcon = { leadingAppIcon() },
        title = title,
        subTitle = subTitle,
        selected = selected,
    )
}

/**
 * A selectable header that can be shown for suggested (featured) widgets option.
 *
 * @param modifier modifier for top level composable of the suggestions header.
 * @param selected if the header is currently selected.
 * @param count number of suggested widgets.
 * @param onSelect action to perform when user selects the header.
 * @param shape shape for the header e.g. depending on position in the list, a different corner.
 * @param selectedBackgroundColor background color when header is [selected].
 * @param unSelectedBackgroundColor background color when header is not [selected].
 */
@Composable
fun SelectableSuggestionsHeader(
    modifier: Modifier,
    selected: Boolean,
    count: Int,
    onSelect: () -> Unit,
    shape: RoundedCornerShape,
    selectedBackgroundColor: Color = ClickableListHeaderDefaults.selectedBackgroundColor,
    unSelectedBackgroundColor: Color = ClickableListHeaderDefaults.unSelectedBackgroundColor,
) {
    SelectableListHeader(
        modifier = modifier,
        selected = selected,
        shape = shape,
        selectedBackgroundColor = selectedBackgroundColor,
        unSelectedBackgroundColor = unSelectedBackgroundColor,
        title = stringResource(R.string.featured_widgets_tab_label),
        subTitle = widgetsCountString(count),
        leadingAppIcon = {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier =
                    Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceBright)
                        .minimumInteractiveComponentSize(),
            )
        },
        onSelect = {
            if (!selected) {
                onSelect()
            }
        },
    )
}

@Composable
private fun WidgetAppHeader(
    modifier: Modifier,
    leadingIcon: @Composable () -> Unit,
    title: String,
    subTitle: String,
    selected: Boolean,
    trailingButton: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .height(height = ListHeaderDimensions.headerHeight)
                .padding(horizontal = ListHeaderDimensions.headerHorizontalPadding),
    ) {
        leadingIcon()
        CenterText(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = ListHeaderDimensions.centerTextHorizontalPadding),
            title = title,
            subTitle = subTitle,
            selected = selected,
        )
        trailingButton?.let { it() }
    }
}

@Composable
private fun CenterText(title: String, subTitle: String, selected: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = WidgetAppListHeaderDefaults.titleTextColor,
            style =
                if (selected) {
                    WidgetAppListHeaderDefaults.selectedTitleTextStyle
                } else {
                    WidgetAppListHeaderDefaults.unSelectedTitleTextStyle
                },
        )
        Text(
            text = subTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = WidgetAppListHeaderDefaults.subTitleTextColor,
            style =
                if (selected) {
                    WidgetAppListHeaderDefaults.selectedSubTitleTextStyle
                } else {
                    WidgetAppListHeaderDefaults.unSelectedSubTitleTextStyle
                },
        )
    }
}

private object ListHeaderDimensions {
    val headerHeight = 80.dp
    val headerHorizontalPadding = 16.dp
    val centerTextHorizontalPadding = 16.dp
}

private object ExpandedListHeaderDefaults {
    val backgroundColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceBright

    val contentExpandAnimationSpec = fadeIn(tween(durationMillis = 500)) + expandVertically()
    val contentCollapseAnimationSpec = fadeOut(tween(durationMillis = 500)) + shrinkVertically()
}

private object ClickableListHeaderDefaults {
    val selectedBackgroundColor
        @Composable get() = MaterialTheme.colorScheme.secondaryContainer

    val unSelectedBackgroundColor
        @Composable get() = Color.Transparent
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object WidgetAppListHeaderDefaults {
    val selectedTitleTextStyle: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMediumEmphasized.copy(fontWeight = FontWeight.Medium)

    val unSelectedTitleTextStyle: TextStyle
        @Composable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)

    val selectedSubTitleTextStyle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)

    val unSelectedSubTitleTextStyle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal)

    val titleTextColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val subTitleTextColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}
