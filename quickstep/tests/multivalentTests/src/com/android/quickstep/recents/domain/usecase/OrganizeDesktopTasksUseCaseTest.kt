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

/** Test for [OrganizeDesktopTasksUseCase] */
@RunWith(AndroidJUnit4::class)
class OrganizeDesktopTasksUseCaseTest {

    private val useCase: OrganizeDesktopTasksUseCase = OrganizeDesktopTasksUseCase()
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
    fun test_emptyTaskBounds_returnsEmptyList() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        val taskBounds = emptyList<DesktopTaskBoundsData>()

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_emptyDesktopBounds_returnsEmptyList() {
        val desktopBounds = Rect(0, 0, 0, 0)
        val taskBounds = listOf(DesktopTaskBoundsData(1, Rect(0, 0, 100, 100), true))

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_singleTask_isCenteredAndScaled() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        val originalAppRect = Rect(0, 0, 800, 1200)
        val taskBounds = listOf(DesktopTaskBoundsData(1, originalAppRect, true))

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).hasSize(1)
        val resultBounds = result[0].bounds
        assertThat(resultBounds.width()).isGreaterThan(0)
        assertThat(resultBounds.height()).isGreaterThan(0)

        // Check aspect ratio is roughly preserved
        val originalAspectRatio = originalAppRect.width().toFloat() / originalAppRect.height()
        val resultAspectRatio = resultBounds.width().toFloat() / resultBounds.height()
        assertThat(resultAspectRatio).isWithin(0.1f).of(originalAspectRatio)

        // availableLayoutBounds will be Rect(20, 20, 980, 1980) after subtracting the margins.
        // Check if the task is centered within effective layout bounds
        val expectedTaskRect = Rect(25, 287, 975, 1713)
        assertThat(result)
            .isEqualTo(
                listOf(
                    DesktopTaskBoundsData(
                        taskId = 1,
                        bounds = expectedTaskRect,
                        shouldBeDisplayedInOverview = true,
                    )
                )
            )
    }

    @Test
    fun test_multiTasks_formRows() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        // Make tasks wide enough so they likely won't all fit in one row
        val taskRect = Rect(0, 0, 600, 400)
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(1, taskRect, true),
                DesktopTaskBoundsData(2, taskRect, true),
                DesktopTaskBoundsData(3, taskRect, true),
            )

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)
        assertThat(result).hasSize(3)
        val bounds1 = result[0].bounds

        // Basic checks: positive dimensions, aspect ratio
        result.forEachIndexed { index, data ->
            assertThat(data.bounds.width()).isGreaterThan(0)
            assertThat(data.bounds.height()).isGreaterThan(0)
            val originalAspectRatio = taskRect.width().toFloat() / taskRect.height()
            val resultAspectRatio = data.bounds.width().toFloat() / data.bounds.height()
            assertThat(resultAspectRatio).isWithin(0.1f).of(originalAspectRatio)
        }

        // Expected bounds, based on the current implementation.
        // The tasks are expected to be arranged in 3 rows.
        val expectedTask1Bounds = Rect(20, 30, 980, 670)
        val expectedTask2Bounds = Rect(20, 680, 980, 1320)
        val expectedTask3Bounds = Rect(20, 1330, 980, 1970)
        val expectedResult =
            listOf(
                DesktopTaskBoundsData(1, expectedTask1Bounds, true),
                DesktopTaskBoundsData(2, expectedTask2Bounds, true),
                DesktopTaskBoundsData(3, expectedTask3Bounds, true),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun test_maxRows_limitsNumberOfRowsEffectively() {
        val desktopBounds = Rect(0, 0, 1000, 550) // Height is somewhat constrained
        val taskRect = Rect(0, 0, 200, 100) // Aspect ratio 2:1
        val tasks =
            listOf(
                DesktopTaskBoundsData(1, taskRect, true),
                DesktopTaskBoundsData(2, taskRect, true),
                DesktopTaskBoundsData(3, taskRect, true),
            )

        // For simplicity, configure maxRows = 1.
        // Effective layout height for multi-row (or single-row if margins are same):
        // 550 - 20 (topMargin) - 20 (bottomMargin) = 510
        // Effective layout width for multi-row:
        // 1000 - 20 (leftMargin) - (20-10) (rightNetMargin from leftRightMarginMultiRows -
        // horizontalPadding) = 970
        // availableLayoutBounds for the main layout logic will be Rect(20, 20, 990, 530)
        //
        // With maxRows = 1, verticalPaddingBetweenTasks = 10:
        // getMinTaskHeightGivenMaxRows = ((510 - 1*10) / (1+1)) + 1 = (500/2) + 1 = 251.
        // This acts as a lower bound for the optimalHeight bisection.
        //
        // If optimalHeight is determined to be 251 (original task aspect ratio 200:100):
        //   Scaled task width = 251 * (200/100) = 502.
        //   Horizontally, in fitWindowRectsInBounds, for the first task:
        //     left (20) + width (502) + horizontalPadding (10) = 532. This fits within
        // layoutBounds.right (990).
        //   For the second task on the same row:
        //     new_left (532) + width (502) + horizontalPadding (10) = 1044. This exceeds
        // layoutBounds.right (990).
        //   So, second task attempts to move to a new row.
        //   New row top = old_top (20) + optimalWindowHeight (251) + verticalPadding (10) = 281.
        //   Check if new row fits vertically: (new_row_top + optimalWindowHeight) >
        // layoutBounds.bottom
        //     (281 + 251) > 530  => 532 > 530. It does NOT fit.
        //   Thus, fitWindowRectsInBounds sets allWindowsFit = false and only Task 1 gets bounds.
        //
        // Expected: Task 1 gets bounds and centered, Tasks 2 and 3 get a small bounds in the center
        // of the screen.
        // Expected Rect for Task 1: Rect(254, 149, 756, 401)
        val config =
            testLayoutConfig.copy(
                // testLayoutConfig has topBottomMarginOneRow = 20
                maxRows = 1,
                minTaskWidth = 50, // Low enough not to dominate height calculation
                verticalPaddingBetweenTasks = 10,
                topMarginMultiRows = 20,
                bottomMarginMultiRows = 20,
                leftRightMarginMultiRows = 20,
                horizontalPaddingBetweenTasks = 10,
            )

        val result = useCase.invoke(desktopBounds, tasks, config)

        assertThat(result).hasSize(3)
        assertThat(result[0].bounds).isEqualTo(Rect(254, 149, 756, 401))
        assertThat(result[1].bounds).isEqualTo(Rect(475, 250, 525, 300))
        assertThat(result[2].bounds).isEqualTo(Rect(475, 250, 525, 300))
    }
}
