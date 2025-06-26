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
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.RenderedDesktopTaskBoundsData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class RemoveTaskAndRebalanceLayoutUseCaseTest {

    private val useCase = RemoveTaskAndRebalanceLayoutUseCase()
    private val testLayoutConfig: DesktopLayoutConfig =
        DesktopLayoutConfig(
            topBottomMarginOneRow = 20,
            topMarginMultiRows = 20,
            bottomMarginMultiRows = 20,
            leftRightMarginOneRow = 20,
            leftRightMarginMultiRows = 20,
            horizontalPaddingBetweenTasks = 10,
            verticalPaddingBetweenTasks = 10,
            minTaskWidth = 100,
            maxRows = 4,
        )

    @Test
    fun removeTask_fromEmptyLayout_returnsEmptyList() {
        val result = useCase(emptyList(), 1, testLayoutConfig)
        assertThat(result).isEmpty()
    }

    // Before:
    // [ T1 ]
    // After (removing T2 - not found):
    // [ T1 ]
    @Test
    fun removeTask_whenTaskNotFoundInSingleItemLayout_returnsOriginalLayout() {
        val currentLayout = listOf(RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)))
        val result = useCase(currentLayout, taskIdToRemove = 2, layoutConfig = testLayoutConfig)
        assertThat(result).isEqualTo(currentLayout)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T1):
    // [ T2 ]
    // [ T3 ]
    @Test
    fun removeFirstRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )
        val result = useCase(currentLayout, taskIdToRemove = 1, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(2, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T2):
    // [ T1 ]
    // [ T3 ]
    @Test
    fun removeMiddleRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 2, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T3):
    // [ T1 ]
    // [ T2 ]
    @Test
    fun removeLastRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 3, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ]
    // [ T3 ]
    // [ T4 ] [ T5 ]
    // After (removing T3):
    // [ T1 ] [ T2 ]
    // [ T4 ] [ T5 ]
    @Test
    fun removeMiddleRowWithSingleTask_fromLayoutWithThreeRowsMixedColumns_rebalancesRemainingTwoRows() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(4, Rect(0, 420, 100, 620)),
                RenderedDesktopTaskBoundsData(5, Rect(110, 420, 210, 620)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 3, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 105, 210, 305)),
                RenderedDesktopTaskBoundsData(4, Rect(0, 315, 100, 515)),
                RenderedDesktopTaskBoundsData(5, Rect(110, 315, 210, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T1):
    // [ T2 ] [ T3 ]
    @Test
    fun removeFirstTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 1, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(2, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T2):
    // [ T1 ] [ T3 ]
    @Test
    fun removeMiddleTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 2, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T3):
    // [ T1 ] [ T2 ]
    @Test
    fun removeLastTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )

        val result = useCase(currentLayout, taskIdToRemove = 3, layoutConfig = testLayoutConfig)

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }
}
