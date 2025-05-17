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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.components.SinglePaneLayoutDimensions.searchBarBottomMargin

/**
 * A layout that shows all the widget picker content in a column like pane.
 *
 * @param searchBar A sticky search bar shown on top in the left pane.
 * @param toolbar an option toolbar shown at bottom of the screen that allows users to select what
 *   to see in the [content]
 * @param content the primary content e.g. widgets expand collapse list.
 */
@Composable
fun SinglePaneLayout(
    searchBar: @Composable () -> Unit,
    toolbar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val topContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            searchBar()
            Spacer(modifier = Modifier.fillMaxWidth().height(searchBarBottomMargin))
            content()
        }
    }

    val bottomContent: @Composable ColumnScope.() -> Unit = { toolbar?.let { it() } }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        topContent()
        bottomContent()
    }
}

private object SinglePaneLayoutDimensions {
    val searchBarBottomMargin = 16.dp
}
