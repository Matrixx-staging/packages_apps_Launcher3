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
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData

/**
 * Removes a specified task from an existing layout and rebalances the remaining tasks. When a task
 * is removed, other tasks on the same row are centered. If the last task on a row is removed, then
 * the whole row is removed and the other rows are centered.
 *
 * TODO(b/424269268) It may be possible to share some logic with [OrganizeDesktopTasksUseCase]
 */
class RemoveTaskAndRebalanceLayoutUseCase {
    /**
     * @param currentLayout The list of [DesktopTaskBoundsData] representing the current layout.
     * @param taskIdToRemove The ID of the task to remove.
     * @return A new list of [DesktopTaskBoundsData] with the task removed and layout rebalanced.
     */
    operator fun invoke(
        currentLayout: List<DesktopTaskBoundsData>,
        taskIdToRemove: Int,
        layoutConfig: DesktopLayoutConfig,
    ): List<DesktopTaskBoundsData> {
        val taskToRemoveData =
            currentLayout.find { it.taskId == taskIdToRemove }
                ?: return currentLayout // Task not found, return original layout

        val remainingTasks = currentLayout.filterNot { it.taskId == taskIdToRemove }
        if (remainingTasks.isEmpty()) {
            return emptyList()
        }

        // Get the overall bounds of the current tasks.
        val overallBounds =
            currentLayout.fold(Rect()) { acc, taskData -> acc.apply { union(taskData.bounds) } }

        val remainingRows = remainingTasks.groupBy { it.bounds.top }.toSortedMap()

        val newLayout = mutableListOf<DesktopTaskBoundsData>()
        // Check if the removed task was on its own row.
        if (currentLayout.count { it.bounds.top == taskToRemoveData.bounds.top } == 1) {
            val layoutCenterY = overallBounds.centerY().toFloat()
            val totalHeight =
                remainingRows.entries.sumOf { (_, tasks) -> tasks.maxOf { it.bounds.height() } } +
                    (remainingRows.size - 1) * layoutConfig.verticalPaddingBetweenTasks

            var currentY = layoutCenterY - totalHeight / 2f
            for ((_, tasks) in remainingRows.entries) {
                for (taskData in tasks) {
                    val newBounds =
                        Rect(
                            taskData.bounds.left,
                            currentY.toInt(),
                            taskData.bounds.right,
                            (currentY + taskData.bounds.height()).toInt(),
                        )
                    newLayout.add(DesktopTaskBoundsData(taskData.taskId, newBounds))
                }
                currentY +=
                    tasks.maxOf { it.bounds.height() } + layoutConfig.verticalPaddingBetweenTasks
            }
        } else {
            for ((rowY, tasks) in remainingRows.entries) {
                // Re-center tasks that were on the same row as the removed task.
                if (rowY != taskToRemoveData.bounds.top) {
                    // This row is not affected, add tasks with their original bounds
                    newLayout.addAll(tasks)
                    continue
                }

                val layoutCenterX = overallBounds.centerX().toFloat()

                // This is the affected row, re-calculate X positions.
                val totalWidth =
                    tasks.sumOf { it.bounds.width() } +
                        (tasks.size - 1) * layoutConfig.horizontalPaddingBetweenTasks
                var currentX = layoutCenterX - totalWidth.toFloat() / 2f
                for (taskData in tasks) {
                    val newBounds =
                        Rect(
                            currentX.toInt(),
                            rowY,
                            (currentX + taskData.bounds.width()).toInt(),
                            rowY + taskData.bounds.height(),
                        )
                    newLayout.add(DesktopTaskBoundsData(taskData.taskId, newBounds))
                    currentX +=
                        taskData.bounds.width() +
                            layoutConfig.horizontalPaddingBetweenTasks.toFloat()
                }
            }
        }

        return newLayout
    }
}
