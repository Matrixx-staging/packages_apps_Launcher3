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

package com.android.launcher3.widgetpicker.ui.components.bottomsheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.contentWindowInsets
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.headerBottomMargin
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerHorizontalPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetInnerTopPadding
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetShape
import com.android.launcher3.widgetpicker.ui.components.bottomsheet.TitledBottomSheetDimens.sheetWindowInsets
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * A bottom sheet with title and description on the top. Intended to serve as a common container
 * structure for different types of widget pickers.
 *
 * @param modifier modifier to be applies to the bottom sheet container.
 * @param title A top level title for the bottom sheet. If title is absent, top header isn't shown.
 * @param description an optional short (1-2 line) description that can be shown below the title.
 * @param heightStyle indicates how much vertical space should the bottom sheet take; see
 *   [ModalBottomSheetHeightStyle].
 * @param showDragHandle whether to show drag handle; e.g. if the content doesn't need scrolling set
 *   this to false.
 * @param onDismissSheet callback to be invoked when the bottom sheet is closed
 * @param content the content to be displayed below the [title] and [description]
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitledBottomSheet(
    modifier: Modifier = Modifier,
    title: String?,
    description: String?,
    heightStyle: ModalBottomSheetHeightStyle,
    showDragHandle: Boolean = true,
    onSheetOpen: () -> Unit,
    onDismissSheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.windowInsetsPadding(sheetWindowInsets)) {
        val animSpec: AnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec()
        val sheetState = remember {
            BottomSheetDismissState(expandCollapseAnimationSpec = animSpec)
        }

        Surface(
            modifier =
                Modifier.semantics { isTraversalGroup = true }
                    .fillMaxSize()
                    .dismissibleBottomSheet(
                        sheetState = sheetState,
                        onSheetOpen = onSheetOpen,
                        onDismissSheet = onDismissSheet,
                        maxHeight = with(density) { maxHeight.toPx() },
                    ),
            color = WidgetPickerTheme.colors.sheetBackground,
            shape = sheetShape,
            content = {
                Column(
                    modifier =
                        Modifier.imePadding()
                            .windowInsetsPadding(contentWindowInsets)
                            .sheetContentHeight(heightStyle, maxHeight)
                            .padding(horizontal = sheetInnerHorizontalPadding)
                            .padding(top = sheetInnerTopPadding.takeIf { !showDragHandle } ?: 0.dp)
                            .dismissableBottomSheetContent(sheetState)
                ) {
                    DecorativeDragHandle(
                        modifier =
                            Modifier.align(alignment = Alignment.CenterHorizontally)
                                .padding(top = sheetInnerTopPadding, bottom = headerBottomMargin)
                    )
                    title?.let { Header(title = title, description = description) }
                    content()
                }
            },
        )
    }
}

@Composable
private fun Header(title: String, description: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = headerBottomMargin).fillMaxWidth(),
    ) {
        Text(
            maxLines = 1,
            text = title,
            textAlign = TextAlign.Center,
            style = WidgetPickerTheme.typography.sheetTitle,
            color = WidgetPickerTheme.colors.sheetTitle,
        )
        description?.let {
            Text(
                maxLines = 2,
                text = it,
                textAlign = TextAlign.Center,
                style = WidgetPickerTheme.typography.sheetDescription,
                color = WidgetPickerTheme.colors.sheetDescription,
            )
        }
    }
}

@Composable
private fun Modifier.sheetContentHeight(
    style: ModalBottomSheetHeightStyle,
    maxHeight: Dp,
): Modifier {
    val heightModifier =
        when (style) {
            ModalBottomSheetHeightStyle.FILL_HEIGHT -> this.fillMaxHeight()

            ModalBottomSheetHeightStyle.WRAP_CONTENT -> this.wrapContentHeight()
        }

    return if (maxHeight > 1200.dp) {
        // Cap the height to max 2/3 of total window height; so the bottom sheet doesn't feel too
        // huge.
        heightModifier.heightIn(max = 2 * maxHeight / 3)
    } else {
        heightModifier
    }
}

@Composable
private fun DecorativeDragHandle(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.outline)
                .size(
                    width = DragHandleDimens.dragHandleWidth,
                    height = DragHandleDimens.dragHandleHeight,
                )
    )
}

/**
 * Describes how should the default height of the bottom sheet look like (excluding the insets such
 * as status bar).
 */
enum class ModalBottomSheetHeightStyle {
    /**
     * Fills the available height; capped to a max for extra tall cases. Useful for cases where
     * irrespective of content, we want it to be expanded fully.
     */
    FILL_HEIGHT,

    /**
     * Wraps the content's height; capped to a max for extra tall cases. Set up vertical scrolling
     * if the content can be longer than the available height. Useful for cases like single app
     * widget picker or pin widget picker that don't need to expand fully.
     */
    WRAP_CONTENT,
}

private object DragHandleDimens {
    val dragHandleHeight = 4.dp
    val dragHandleWidth = 32.dp
}

private object TitledBottomSheetDimens {
    val sheetInnerTopPadding = 16.dp
    val sheetInnerHorizontalPadding = 10.dp
    val headerBottomMargin = 16.dp

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val sheetWindowInsets: WindowInsets
        @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout)

    val contentWindowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.safeDrawing.only(sides = WindowInsetsSides.Bottom + WindowInsetsSides.Top)
}

/** Default values for the [TitledBottomSheet] component. */
object TitledBottomSheetDefaults {
    /** Max animation duration for the bottom sheet to fully expand. */
    const val SLIDE_IN_ANIMATION_DURATION: Long = 400L
}
