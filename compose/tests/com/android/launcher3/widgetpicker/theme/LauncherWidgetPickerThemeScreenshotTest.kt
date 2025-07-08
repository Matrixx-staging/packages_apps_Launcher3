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

package com.android.launcher3.widgetpicker.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import com.google.android.apps.nexuslauncher.imagecomparison.goldenpathmanager.ViewScreenshotGoldenPathManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.getEmulatedDevicePathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

/**
 * A theme test that enables testing that correct theme attributes are picked when converting colors
 * and styles from xml resources to jetpack compose.
 *
 * This separate test enables verifying color and font without running entire widget picker and
 * hence enables focus on styling diffs. This also serves as a style sheet to share with UX.
 */
@RunWith(ParameterizedAndroidJunit4::class)
class LauncherWidgetPickerThemeScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule val screenshotRule = composeScreenshotTestRule(emulationSpec)

    @Test
    fun widgetPicker_styles() {
        screenshotRule.screenshotTest("widgetPicker_styles") { LauncherWidgetPickerStyles() }
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(Displays.Phone, isLandscape = false)
        }

        private fun composeScreenshotTestRule(
            emulationSpec: DeviceEmulationSpec,
            enforcePerfectPixelMatch: Boolean = false,
        ): ComposeScreenshotTestRule {
            return ComposeScreenshotTestRule(
                emulationSpec = emulationSpec,
                pathManager =
                    ViewScreenshotGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
                enforcePerfectPixelMatch = enforcePerfectPixelMatch,
            )
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
private fun LauncherWidgetPickerStyles() {
    LauncherWidgetPickerTheme {
        Column(modifier = Modifier.background(Color.Gray).padding(2.dp)) {
            WidgetSheetHeaderStyle()
            Divider()
            WidgetDetailsStyle()
            Divider()
            WidgetSearchBarStyle()
            Divider()
            AddButtonStyle()
            Divider()
            WidgetPickerToolbarStyle()
            Divider()
            WidgetListExpandableHeaderStyle()
            Divider()
            WidgetListSelectableHeaderStyle()
        }
    }
}

@Composable
private fun Divider() {
    Spacer(modifier = Modifier.fillMaxWidth().height(4.dp))
}

@Composable
private fun WidgetSheetHeaderStyle() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.sheetBackground)
                .padding(8.dp)
    ) {
        Text(
            text = "Widget Picker's Title",
            color = WidgetPickerTheme.colors.sheetTitle,
            style = WidgetPickerTheme.typography.sheetTitle,
        )
        Text(
            text = "This is widget picker's long description",
            color = WidgetPickerTheme.colors.sheetDescription,
            style = WidgetPickerTheme.typography.sheetDescription,
        )
    }
}

@Composable
private fun WidgetDetailsStyle() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.widgetsContainerBackground)
                .padding(8.dp)
    ) {
        Text(
            text = "Widget's Label",
            color = WidgetPickerTheme.colors.widgetLabel,
            style = WidgetPickerTheme.typography.widgetLabel,
        )
        Text(
            text = "1 x 1",
            color = WidgetPickerTheme.colors.widgetSpanText,
            style = WidgetPickerTheme.typography.widgetSpanText,
        )
        Text(
            text = "This is widget's description",
            color = WidgetPickerTheme.colors.widgetDescription,
            style = WidgetPickerTheme.typography.widgetDescription,
        )
    }
}

@Composable
private fun AddButtonStyle() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.addButtonBackground)
                .padding(2.dp)
    ) {
        Text(
            text = "Add button",
            color = WidgetPickerTheme.colors.addButtonContent,
            style = WidgetPickerTheme.typography.addWidgetButtonLabel,
        )
    }
}

@Composable
private fun WidgetSearchBarStyle() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.searchBarBackground)
                .padding(8.dp)
    ) {
        Text(
            text = "Search",
            color = WidgetPickerTheme.colors.searchBarText,
            style = WidgetPickerTheme.typography.searchBarText,
        )
    }
}

@Composable
private fun WidgetPickerToolbarStyle() {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.toolbarBackground)
                .padding(8.dp)
    ) {
        Box(
            modifier =
                Modifier.background(WidgetPickerTheme.colors.toolbarTabUnSelectedBackground)
                    .padding(2.dp)
        ) {
            Text(
                text = "Tab",
                color = WidgetPickerTheme.colors.toolbarUnSelectedTabContent,
                style = WidgetPickerTheme.typography.toolbarUnSelectedTabLabel,
            )
        }
        Box(
            modifier =
                Modifier.background(WidgetPickerTheme.colors.toolbarTabSelectedBackground)
                    .padding(2.dp)
        ) {
            Text(
                text = "Selected Tab",
                color = WidgetPickerTheme.colors.toolbarSelectedTabContent,
                style = WidgetPickerTheme.typography.toolbarSelectedTabLabel,
            )
        }
    }
}

@Composable
private fun WidgetListExpandableHeaderStyle() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(WidgetPickerTheme.colors.expandableListItemsBackground)
                .padding(8.dp)
    ) {
        Text(
            text = "Expandable App Title",
            color = WidgetPickerTheme.colors.expandableListHeaderTitle,
            style = WidgetPickerTheme.typography.expandableListHeaderTitle,
        )
        Text(
            text = "x widgets, y shortcuts",
            color = WidgetPickerTheme.colors.expandableListHeaderTitle,
            style = WidgetPickerTheme.typography.expandableListHeaderTitle,
        )
    }
}

@Composable
private fun WidgetListSelectableHeaderStyle() {
    Row(modifier = Modifier.background(WidgetPickerTheme.colors.sheetBackground)) {
        Column(
            modifier =
                Modifier.weight(0.5f)
                    .background(WidgetPickerTheme.colors.selectedListHeaderBackground)
                    .padding(8.dp)
        ) {
            Text(
                text = "Selected App Title",
                color = WidgetPickerTheme.colors.selectedListHeaderTitle,
                style = WidgetPickerTheme.typography.selectedListHeaderTitle,
            )
            Text(
                text = "x widgets, y shortcuts",
                color = WidgetPickerTheme.colors.selectedListHeaderSubTitle,
                style = WidgetPickerTheme.typography.selectedListHeaderSubTitle,
            )
        }
        Column(
            modifier =
                Modifier.weight(0.5f)
                    .background(WidgetPickerTheme.colors.unselectedListHeaderBackground)
                    .padding(8.dp)
        ) {
            Text(
                text = "Unselected App Title",
                color = WidgetPickerTheme.colors.unSelectedListHeaderTitle,
                style = WidgetPickerTheme.typography.unSelectedListHeaderTitle,
            )
            Text(
                text = "x widgets, y shortcuts",
                color = WidgetPickerTheme.colors.unSelectedListHeaderSubTitle,
                style = WidgetPickerTheme.typography.unSelectedListHeaderSubTitle,
            )
        }
    }
}
