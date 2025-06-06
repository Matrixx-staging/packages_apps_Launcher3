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

package com.android.quickstep.recents.domain.usecase

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [DesktopLayoutUtils] */
@RunWith(AndroidJUnit4::class)
class DesktopLayoutUtilsTest {

    private val testLayoutConfig =
        DesktopLayoutConfig(
            topBottomMarginOneRow = 20, // Not directly used by getRequiredHeightForMinWidth
            topMarginMultiRows = 20, // Not directly used by getRequiredHeightForMinWidth
            bottomMarginMultiRows = 20, // Not directly used by getRequiredHeightForMinWidth
            leftRightMarginOneRow = 20, // Not directly used by getRequiredHeightForMinWidth
            leftRightMarginMultiRows = 20, // Not directly used by getRequiredHeightForMinWidth
            horizontalPaddingBetweenTasks = 10, // Not directly used by getRequiredHeightForMinWidth
            verticalPaddingBetweenTasks = 10, // Used as default/minimum
            minTaskWidth = 100, // Key input for the function
            maxRows = 4, // Not directly used by getRequiredHeightForMinWidth
        )

    @Test
    fun getRequiredHeightForMinWidth_minTaskWidthIsZero_returnsDefaultMinHeight() {
        val taskBounds = listOf(DesktopTaskBoundsData(1, Rect(0, 0, 200, 300), true))
        val config = testLayoutConfig.copy(minTaskWidth = 0, verticalPaddingBetweenTasks = 50)
        val expectedHeight = config.verticalPaddingBetweenTasks

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getRequiredHeightForMinWidth_singleTask_calculatesHeight() {
        // Task: 200x300, minWidth: 100. Expected height: (100 * 300) / 200 = 150
        val taskBounds = listOf(DesktopTaskBoundsData(1, Rect(0, 0, 200, 300), true))
        val config = testLayoutConfig.copy(minTaskWidth = 100, verticalPaddingBetweenTasks = 10)
        val expectedHeight = 150

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getRequiredHeightForMinWidth_multipleTasks_calculatesMaxHeight() {
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(
                    1,
                    Rect(0, 0, 200, 300),
                    true,
                ), // req height for 100 width: 150
                DesktopTaskBoundsData(
                    2,
                    Rect(0, 0, 100, 200),
                    true,
                ), // req height for 100 width: 200
                DesktopTaskBoundsData(
                    3,
                    Rect(0, 0, 400, 400),
                    true,
                ), // req height for 100 width: 100
            )
        val config = testLayoutConfig.copy(minTaskWidth = 100, verticalPaddingBetweenTasks = 10)
        val expectedHeight = 200 // Max of 150, 200, 100

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getRequiredHeightForMinWidth_taskWithZeroDimension_usesDefaultMinHeightIfAllInvalid() {
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(1, Rect(0, 0, 0, 100), true), // Invalid width
                DesktopTaskBoundsData(2, Rect(0, 0, 100, 0), true), // Invalid height
            )
        val config = testLayoutConfig.copy(minTaskWidth = 100, verticalPaddingBetweenTasks = 50)
        val expectedHeight = config.verticalPaddingBetweenTasks // 50

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getRequiredHeightForMinWidth_taskWithZeroDimension_ignoredIfOthersValid() {
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(
                    1,
                    Rect(0, 0, 200, 300),
                    true,
                ), // req height for 100 width: 150
                DesktopTaskBoundsData(2, Rect(0, 0, 0, 100), true), // Invalid width, ignored
                DesktopTaskBoundsData(3, Rect(0, 0, 100, 0), true), // Invalid height, ignored
            )
        val config = testLayoutConfig.copy(minTaskWidth = 100, verticalPaddingBetweenTasks = 10)
        val expectedHeight = 150

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getRequiredHeightForMinWidth_calculatedHeightLessThanDefault_returnsDefault() {
        // Task: 1000x100, minWidth: 100. Expected height: (100 * 100) / 1000 = 10
        // But verticalPaddingBetweenTasks is 50, so 50 should be returned.
        val taskBounds = listOf(DesktopTaskBoundsData(1, Rect(0, 0, 1000, 100), true))
        val config = testLayoutConfig.copy(minTaskWidth = 100, verticalPaddingBetweenTasks = 50)
        val expectedHeight = 50

        val resultHeight = DesktopLayoutUtils.getRequiredHeightForMinWidth(taskBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getMinTaskHeightGivenMaxRows_maxRowsIsZero_returnsVerticalPadding() {
        val availableBounds = Rect(0, 0, 1000, 1000)
        val config = testLayoutConfig.copy(maxRows = 0, verticalPaddingBetweenTasks = 15)
        val resultHeight = DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(availableBounds, config)

        assertThat(resultHeight).isEqualTo(config.verticalPaddingBetweenTasks)
    }

    @Test
    fun getMinTaskHeightGivenMaxRows_standardCase_calculatesCorrectHeight() {
        val availableBounds = Rect(0, 0, 1000, 1000)
        val config = testLayoutConfig.copy(maxRows = 3, verticalPaddingBetweenTasks = 10)
        // totalPadding = 3 * 10 = 30
        // heightForTaskContents = 1000 - 30 = 970
        // result = (970 / (3 + 1) + 1).toInt() = (970 / 4 + 1).toInt() = (242.5 + 1).toInt() = 243
        val expectedHeight = 243

        val resultHeight = DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(availableBounds, config)
        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getMinTaskHeightGivenMaxRows_calculatedHeightLessThanPadding_returnsVerticalPadding() {
        val availableBounds = Rect(0, 0, 1000, 100)
        val config = testLayoutConfig.copy(maxRows = 4, verticalPaddingBetweenTasks = 20)
        // totalPadding = 4 * 20 = 80
        // heightForTaskContents = 100 - 80 = 20
        // result = (20 / (4 + 1) + 1).toInt() = (20 / 5 + 1).toInt() = (4 + 1).toInt() = 5
        // max(result, 20) = 20
        val expectedHeight = 20

        val resultHeight = DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(availableBounds, config)
        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getMinTaskHeightGivenMaxRows_exactFitForMaxRowsPlusOne_returnsHeightPlusOne() {
        // Test the +1 logic carefully.
        // If heightForTaskContents / (maxRows + 1) is an integer, e.g., 25.
        // Then result should be 25 + 1 = 26.
        val availableBounds = Rect(0, 0, 1000, 130) // height = 130
        val config = testLayoutConfig.copy(maxRows = 3, verticalPaddingBetweenTasks = 10)
        // totalPadding = 3 * 10 = 30
        // heightForTaskContents = 130 - 30 = 100
        // result = (100 / (3 + 1) + 1).toInt() = (100 / 4 + 1).toInt() = (25 + 1).toInt() = 26
        // max(result, 10) = 26
        val expectedHeight = 26

        val resultHeight = DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(availableBounds, config)

        assertThat(resultHeight).isEqualTo(expectedHeight)
    }

    @Test
    fun getMinTaskHeightGivenMaxRows_zeroVerticalPadding_calculatesCorrectHeight() {
        val availableBounds = Rect(0, 0, 1000, 100)
        val config = testLayoutConfig.copy(maxRows = 3, verticalPaddingBetweenTasks = 0)
        // totalPadding = 0
        // heightForTaskContents = 100 - 0 = 100
        // result = (100 / (3 + 1) + 1).toInt() = (100 / 4 + 1).toInt() = (25 + 1).toInt() = 26
        // max(result, 0) = 26
        val expectedHeight = 26

        val resultHeight = DesktopLayoutUtils.getMinTaskHeightGivenMaxRows(availableBounds, config)
        assertThat(resultHeight).isEqualTo(expectedHeight)
    }
}
