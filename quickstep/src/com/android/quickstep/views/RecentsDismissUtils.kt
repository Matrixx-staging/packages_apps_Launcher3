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

package com.android.quickstep.views

import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.R
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.TaskGridNavHelper
import com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY
import com.android.quickstep.views.TaskView.Companion.GRID_END_TRANSLATION_X
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Helper class for [RecentsView]. This util class contains refactored and extracted functions from
 * RecentsView related to TaskView dismissal.
 */
class RecentsDismissUtils(private val recentsView: RecentsView<*, *>) {

    /**
     * Runs the default spring animation when a dragged task view in overview is released.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will spring
     * in response to the perceived impact of the settling task.
     */
    fun createTaskDismissSettlingSpringAnimation(draggedTaskView: TaskView): SpringAnimation? {
        with(recentsView) {
            draggedTaskView.getThumbnailBounds(mTempRect, /* relativeToDragLayer= */ true)
            val secondaryLayerDimension: Int =
                pagedOrientationHandler.getSecondaryDimension(
                    (mContainer as ActivityContext).getDragLayer()
                )
            val verticalFactor = pagedOrientationHandler.getTaskDismissVerticalDirection().toFloat()
            val dismissLength =
                (pagedOrientationHandler.getTaskDismissLength(secondaryLayerDimension, mTempRect) *
                        verticalFactor)
                    .toInt()
            val dismissThreshold = (dismissLength * DEFAULT_DISMISS_THRESHOLD_FRACTION).toInt()
            val velocity = mTempRect.height().toFloat()
            return createTaskDismissSettlingSpringAnimation(
                draggedTaskView,
                velocity,
                isDismissing = true,
                dismissLength,
                dismissThreshold,
                dismissLength.toFloat(),
            )
        }
    }

    /**
     * Runs the spring animations when a dragged task view in overview is released.
     *
     * <p>When a task dismiss is cancelled, the task will return to its original position via a
     * spring animation. As it passes the threshold of its settling state, its neighbors will spring
     * in response to the perceived impact of the settling task.
     */
    fun createTaskDismissSettlingSpringAnimation(
        draggedTaskView: TaskView?,
        velocity: Float,
        isDismissing: Boolean,
        dismissLength: Int,
        dismissThreshold: Int,
        finalPosition: Float,
        onEndRunnable: () -> Unit = {},
    ): SpringAnimation? {
        draggedTaskView ?: return null
        val taskDismissFloatProperty =
            FloatPropertyCompat.createFloatPropertyCompat(
                draggedTaskView.secondaryDismissTranslationProperty
            )
        val minVelocity =
            recentsView.pagedOrientationHandler.getSecondaryDimension(draggedTaskView).toFloat()
        val startVelocity = abs(velocity).coerceAtLeast(minVelocity) * velocity.sign
        var previousDisplacement = taskDismissFloatProperty.getValue(draggedTaskView)
        // Animate dragged task towards dismissal or rest state.
        val draggedTaskViewSpringAnimation =
            SpringAnimation(draggedTaskView, taskDismissFloatProperty)
                .setSpring(createExpressiveDismissSpringForce())
                .setStartVelocity(startVelocity)
                .addUpdateListener { animation, currentDisplacement, _ ->
                    // Play haptic as task crosses dismiss threshold from above or below.
                    val previousBeyondThreshold = abs(previousDisplacement) >= abs(dismissThreshold)
                    val currentBeyondThreshold = abs(currentDisplacement) >= abs(dismissThreshold)
                    if (previousBeyondThreshold != currentBeyondThreshold) {
                        MSDLPlayerWrapper.INSTANCE.get(recentsView.context)
                            .playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
                    }
                    previousDisplacement = currentDisplacement

                    if (draggedTaskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value =
                                taskDismissFloatProperty.getValue(draggedTaskView)
                        }
                        recentsView.redrawLiveTile()
                    }
                    if (isDismissing && abs(currentDisplacement) >= abs(dismissLength)) {
                        animation.cancel()
                    }
                }
                .addEndListener { _, _, _, _ ->
                    if (isDismissing) {
                        if (!recentsView.showAsGrid() || enableGridOnlyOverview()) {
                            runTaskGridReflowSpringAnimation(
                                draggedTaskView,
                                getDismissedTaskGapForReflow(draggedTaskView),
                                onEndRunnable,
                            )
                        } else {
                            recentsView.dismissTaskView(
                                draggedTaskView,
                                /* animateTaskView = */ false,
                                /* removeTask = */ true,
                            )
                            onEndRunnable()
                        }
                    } else {
                        recentsView.onDismissAnimationEnds()
                        onEndRunnable()
                    }
                }
        if (!isDismissing) {
            addNeighborSettlingSpringAnimations(
                draggedTaskView,
                draggedTaskViewSpringAnimation,
                driverProgressThreshold = 0f,
                isSpringDirectionVertical = true,
                minVelocity = startVelocity,
            )
        }
        return draggedTaskViewSpringAnimation.apply { animateToFinalPosition(finalPosition) }
    }

    private fun addNeighborSettlingSpringAnimations(
        draggedTaskView: TaskView,
        springAnimationDriver: SpringAnimation,
        tasksToExclude: List<TaskView> = emptyList(),
        driverProgressThreshold: Float,
        isSpringDirectionVertical: Boolean,
        minVelocity: Float,
    ) {
        // Empty spring animation exists for conditional start, and to drive neighboring springs.
        val neighborsToSettle =
            SpringAnimation(FloatValueHolder()).setSpring(createExpressiveDismissSpringForce())

        // Add tasks before dragged index, fanning out from the dragged task.
        // The order they are added matters, as each spring drives the next.
        var previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDraggedTask(draggedTaskView, towardsStart = true)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                    )
            }
        // Add tasks after dragged index, fanning out from the dragged task.
        // The order they are added matters, as each spring drives the next.
        previousNeighbor = neighborsToSettle
        getTasksOffsetPairAdjacentToDraggedTask(draggedTaskView, towardsStart = false)
            .filter { (taskView, _) -> !tasksToExclude.contains(taskView) }
            .forEach { (taskView, offset) ->
                previousNeighbor =
                    createNeighboringTaskViewSpringAnimation(
                        taskView,
                        offset * ADDITIONAL_DISMISS_DAMPING_RATIO,
                        previousNeighbor,
                        isSpringDirectionVertical,
                    )
            }

        val isCurrentDisplacementAboveOrigin =
            recentsView.pagedOrientationHandler.isGoingUp(
                draggedTaskView.secondaryDismissTranslationProperty.get(draggedTaskView),
                recentsView.isRtl,
            )
        addThresholdSpringAnimationTrigger(
            springAnimationDriver,
            progressThreshold = driverProgressThreshold,
            neighborsToSettle,
            isCurrentDisplacementAboveOrigin,
            minVelocity,
        )
    }

    /** As spring passes threshold for the first time, run conditional spring with velocity. */
    private fun addThresholdSpringAnimationTrigger(
        springAnimationDriver: SpringAnimation,
        progressThreshold: Float,
        conditionalSpring: SpringAnimation,
        isCurrentDisplacementAboveOrigin: Boolean,
        minVelocity: Float,
    ) {
        val runSettlingAtVelocity = { velocity: Float ->
            conditionalSpring.setStartVelocity(velocity).animateToFinalPosition(0f)
        }
        if (isCurrentDisplacementAboveOrigin) {
            var lastPosition = 0f
            var startSettling = false
            springAnimationDriver.addUpdateListener { _, value, velocity ->
                // We do not compare to the threshold directly, as the update listener
                // does not necessarily hit every value. Do not check again once it has started
                // settling, as a spring can bounce past the end value multiple times.
                if (startSettling) return@addUpdateListener
                if (
                    lastPosition < progressThreshold && value >= progressThreshold ||
                        lastPosition > progressThreshold && value <= progressThreshold
                ) {
                    startSettling = true
                }
                lastPosition = value
                if (startSettling) {
                    runSettlingAtVelocity(velocity)
                }
            }
        } else {
            // Run settling animations immediately when displacement is already below settled state.
            runSettlingAtVelocity(minVelocity)
        }
    }

    /**
     * Gets pairs of (TaskView, offset) adjacent the dragged task in visual order.
     *
     * <p>Gets tasks either before or after the dragged task along with their offset from it. The
     * offset is the distance between indices for carousels, or distance between columns for grids.
     */
    private fun getTasksOffsetPairAdjacentToDraggedTask(
        draggedTaskView: TaskView,
        towardsStart: Boolean,
    ): Sequence<Pair<TaskView, Int>> {
        if (recentsView.showAsGrid()) {
            val taskGridNavHelper =
                TaskGridNavHelper(
                    recentsView.mUtils.getTopRowIdArray(),
                    recentsView.mUtils.getBottomRowIdArray(),
                    recentsView.mUtils.getLargeTaskViewIds(),
                    hasAddDesktopButton = false,
                )
            return taskGridNavHelper
                .gridTaskViewIdOffsetPairInTabOrderSequence(
                    draggedTaskView.taskViewId,
                    towardsStart,
                )
                .mapNotNull { (taskViewId, columnOffset) ->
                    recentsView.getTaskViewFromTaskViewId(taskViewId)?.let { taskView ->
                        Pair(taskView, columnOffset)
                    }
                }
        } else {
            val taskViewList = recentsView.mUtils.taskViews.toList()
            val draggedTaskViewIndex = taskViewList.indexOf(draggedTaskView)

            return if (towardsStart) {
                taskViewList
                    .take(draggedTaskViewIndex)
                    .reversed()
                    .mapIndexed { index, taskView -> Pair(taskView, index + 1) }
                    .asSequence()
            } else {
                taskViewList
                    .takeLast(taskViewList.size - draggedTaskViewIndex - 1)
                    .mapIndexed { index, taskView -> Pair(taskView, index + 1) }
                    .asSequence()
            }
        }
    }

    /** Creates a neighboring task view spring, driven by the spring of its neighbor. */
    private fun createNeighboringTaskViewSpringAnimation(
        taskView: TaskView,
        dampingOffsetRatio: Float,
        previousNeighborSpringAnimation: SpringAnimation,
        springingDirectionVertical: Boolean,
    ): SpringAnimation {
        val springProperty =
            if (springingDirectionVertical) taskView.secondaryDismissTranslationProperty
            else taskView.primaryDismissTranslationProperty
        val neighboringTaskViewSpringAnimation =
            SpringAnimation(taskView, FloatPropertyCompat.createFloatPropertyCompat(springProperty))
                .setSpring(createExpressiveDismissSpringForce(dampingOffsetRatio))
        // Update live tile on spring animation.
        if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
            neighboringTaskViewSpringAnimation.addUpdateListener { _, _, _ ->
                recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                    val taskTranslation =
                        if (springingDirectionVertical) {
                            remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation
                        } else {
                            remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation
                        }
                    taskTranslation.value = springProperty.get(taskView)
                }
                recentsView.redrawLiveTile()
            }
        }
        // Drive current neighbor's spring with the previous neighbor's.
        previousNeighborSpringAnimation.addUpdateListener { _, value, _ ->
            neighboringTaskViewSpringAnimation.animateToFinalPosition(value)
        }
        return neighboringTaskViewSpringAnimation
    }

    private fun createExpressiveDismissSpringForce(dampingRatioOffset: Float = 0f): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce()
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_y_damping_ratio) +
                    dampingRatioOffset
            )
            .setStiffness(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_y_stiffness)
            )
    }

    private fun createExpressiveGridReflowSpringForce(
        finalPosition: Float = Float.MAX_VALUE
    ): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce(finalPosition)
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_x_damping_ratio)
            )
            .setStiffness(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_task_trans_x_stiffness)
            )
    }

    private fun createExpressiveDismissAlphaSpringForce(): SpringForce {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        return SpringForce()
            .setDampingRatio(
                resourceProvider.getFloat(R.dimen.expressive_dismiss_effects_damping_ratio)
            )
            .setStiffness(resourceProvider.getFloat(R.dimen.expressive_dismiss_effects_stiffness))
    }

    /** Animates RecentsView's scale to the provided value, using spring animations. */
    fun animateRecentsScale(scale: Float): SpringAnimation {
        val resourceProvider = DynamicResource.provider(recentsView.mContainer)
        val dampingRatio = resourceProvider.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio)
        val stiffness = resourceProvider.getFloat(R.dimen.swipe_up_rect_scale_stiffness)

        // Spring which sets the Recents scale on update. This is needed, as the SpringAnimation
        // struggles to animate small values like changing recents scale from 0.9 to 1. So
        // we animate over a larger range (e.g. 900 to 1000) and convert back to the required value.
        // (This is instead of converting RECENTS_SCALE_PROPERTY to a FloatPropertyCompat and
        // animating it directly via springs.)
        val initialRecentsScaleSpringValue =
            RECENTS_SCALE_SPRING_MULTIPLIER * RECENTS_SCALE_PROPERTY.get(recentsView)
        return SpringAnimation(FloatValueHolder(initialRecentsScaleSpringValue))
            .setSpring(
                SpringForce(initialRecentsScaleSpringValue)
                    .setDampingRatio(dampingRatio)
                    .setStiffness(stiffness)
            )
            .addUpdateListener { _, value, _ ->
                RECENTS_SCALE_PROPERTY.setValue(
                    recentsView,
                    value / RECENTS_SCALE_SPRING_MULTIPLIER,
                )
            }
            .apply { animateToFinalPosition(RECENTS_SCALE_SPRING_MULTIPLIER * scale) }
    }

    /** Animates with springs the TaskViews beyond the dismissed task to fill the gap it left. */
    private fun runTaskGridReflowSpringAnimation(
        dismissedTaskView: TaskView,
        dismissedTaskGap: Float,
        onEndRunnable: () -> Unit,
    ) {
        // Empty spring animation exists for conditional start, and to drive neighboring springs.
        val springAnimationDriver =
            SpringAnimation(FloatValueHolder())
                .setSpring(createExpressiveGridReflowSpringForce(finalPosition = dismissedTaskGap))
        val towardsStart = if (recentsView.isRtl) dismissedTaskGap < 0 else dismissedTaskGap > 0

        var tasksToReflow: List<TaskView>
        // Build the chains of Spring Animations
        when {
            !recentsView.showAsGrid() -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.taskViews.toList(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
            dismissedTaskView.isLargeTile -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getLargeTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                val lastSpringAnimation =
                    buildDismissReflowSpringAnimationChain(
                        tasksToReflow,
                        dismissedTaskGap,
                        previousSpring = springAnimationDriver,
                    )
                // Add all top and bottom grid tasks when animating towards the end of the grid.
                if (!towardsStart) {
                    tasksToReflow += recentsView.mUtils.getTopRowTaskViews()
                    tasksToReflow += recentsView.mUtils.getBottomRowTaskViews()
                    buildDismissReflowSpringAnimationChain(
                        recentsView.mUtils.getTopRowTaskViews(),
                        dismissedTaskGap,
                        previousSpring = lastSpringAnimation,
                    )
                    buildDismissReflowSpringAnimationChain(
                        recentsView.mUtils.getBottomRowTaskViews(),
                        dismissedTaskGap,
                        previousSpring = lastSpringAnimation,
                    )
                }
            }
            recentsView.isOnGridBottomRow(dismissedTaskView) -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getBottomRowTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
            else -> {
                tasksToReflow =
                    getTasksToReflow(
                        recentsView.mUtils.getTopRowTaskViews(),
                        dismissedTaskView,
                        towardsStart,
                    )
                buildDismissReflowSpringAnimationChain(
                    tasksToReflow,
                    dismissedTaskGap,
                    previousSpring = springAnimationDriver,
                )
            }
        }

        val runImmediately = tasksToReflow.isEmpty()
        if (runImmediately) {
            runGridEndTranslation(dismissedTaskView, onEndRunnable, DISMISS_IMMEDIATE_DURATION)
        } else {
            addNeighborSettlingSpringAnimations(
                dismissedTaskView,
                springAnimationDriver,
                tasksToExclude = tasksToReflow,
                driverProgressThreshold = dismissedTaskGap,
                isSpringDirectionVertical = false,
                minVelocity = 0f,
            )
            springAnimationDriver.apply {
                addEndListener { _, _, _, _ ->
                    runGridEndTranslation(
                        dismissedTaskView,
                        onEndRunnable,
                        DISMISS_DEFAULT_DURATION,
                    )
                }
                animateToFinalPosition(dismissedTaskGap)
            }
        }
    }

    private fun getDismissedTaskGapForReflow(dismissedTaskView: TaskView): Float {
        // If current page is beyond last TaskView's index, use last TaskView to calculate offset.
        val lastTaskViewIndex = recentsView.indexOfChild(recentsView.mUtils.getLastTaskView())
        val currentPage = recentsView.currentPage.coerceAtMost(lastTaskViewIndex)
        val dismissHorizontalFactor =
            when {
                dismissedTaskView.isGridTask -> 1f
                currentPage == lastTaskViewIndex -> -1f
                recentsView.indexOfChild(dismissedTaskView) < currentPage -> -1f
                else -> 1f
            } * (if (recentsView.isRtl) 1f else -1f)

        return (recentsView.pagedOrientationHandler.getPrimarySize(dismissedTaskView) +
            recentsView.pageSpacing) * dismissHorizontalFactor
    }

    private fun getTasksToReflow(
        taskViews: List<TaskView>,
        dismissedTaskView: TaskView,
        towardsStart: Boolean,
    ): List<TaskView> {
        val dismissedTaskViewIndex = taskViews.indexOf(dismissedTaskView)
        if (dismissedTaskViewIndex == -1) {
            return emptyList()
        }
        return if (towardsStart) {
            taskViews.take(dismissedTaskViewIndex).reversed()
        } else {
            taskViews.takeLast(taskViews.size - dismissedTaskViewIndex - 1)
        }
    }

    private fun willTaskBeVisibleAfterDismiss(taskView: TaskView, taskTranslation: Int): Boolean {
        val screenStart = recentsView.pagedOrientationHandler.getPrimaryScroll(recentsView)
        val screenEnd =
            screenStart + recentsView.pagedOrientationHandler.getMeasuredSize(recentsView)
        return recentsView.isTaskViewWithinBounds(
            taskView,
            screenStart,
            screenEnd,
            /* taskViewTranslation = */ taskTranslation,
        )
    }

    /** Builds a chain of spring animations for task reflow after dismissal */
    private fun buildDismissReflowSpringAnimationChain(
        taskViews: Iterable<TaskView>,
        dismissedTaskGap: Float,
        previousSpring: SpringAnimation,
    ): SpringAnimation {
        var lastTaskViewSpring = previousSpring
        taskViews
            .filter { taskView ->
                willTaskBeVisibleAfterDismiss(taskView, dismissedTaskGap.roundToInt())
            }
            .forEach { taskView ->
                val taskViewSpringAnimation =
                    SpringAnimation(
                            taskView,
                            FloatPropertyCompat.createFloatPropertyCompat(
                                taskView.primaryDismissTranslationProperty
                            ),
                        )
                        .setSpring(createExpressiveGridReflowSpringForce(dismissedTaskGap))
                // Update live tile on spring animation.
                if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                    taskViewSpringAnimation.addUpdateListener { _, _, _ ->
                        recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                            remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation.value =
                                taskView.primaryDismissTranslationProperty.get(taskView)
                        }
                        recentsView.redrawLiveTile()
                    }
                }
                lastTaskViewSpring.addUpdateListener { _, value, _ ->
                    taskViewSpringAnimation.animateToFinalPosition(value)
                }
                lastTaskViewSpring = taskViewSpringAnimation
            }
        return lastTaskViewSpring
    }

    /** Animates the grid to compensate the clear all gap after dismissal. */
    private fun runGridEndTranslation(
        dismissedTaskView: TaskView,
        onEndRunnable: () -> Unit,
        dismissDuration: Int,
    ) {
        val runGridEndAnimationAndRelayout = { gridEndData: GridEndData ->
            recentsView.expressiveDismissTaskView(
                dismissedTaskView,
                onEndRunnable,
                dismissDuration,
                gridEndData,
            )
        }
        val gridEndData = getGridEndData(dismissedTaskView)
        val gridEndOffset = gridEndData.gridEndOffset
        if (gridEndOffset == 0f) {
            runGridEndAnimationAndRelayout(gridEndData)
            return
        }

        // Create spring animation to drive all task grid translation simultaneously.
        val gridEndSpring =
            SpringAnimation(FloatValueHolder())
                .setSpring(createExpressiveGridReflowSpringForce(gridEndOffset))
        recentsView.mUtils.taskViews.forEach { taskView ->
            val taskViewGridEndSpringAnimation =
                SpringAnimation(
                        taskView,
                        FloatPropertyCompat.createFloatPropertyCompat(GRID_END_TRANSLATION_X),
                    )
                    .setSpring(createExpressiveGridReflowSpringForce(gridEndOffset))
            // Update live tile on spring animation.
            if (taskView.isRunningTask && recentsView.enableDrawingLiveTile) {
                taskViewGridEndSpringAnimation.addUpdateListener { _, _, _ ->
                    recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                        remoteTargetHandle.taskViewSimulator.taskPrimaryTranslation.value =
                            GRID_END_TRANSLATION_X.get(taskView)
                    }
                    recentsView.redrawLiveTile()
                }
            }
            gridEndSpring.addUpdateListener { _, value, _ ->
                taskViewGridEndSpringAnimation.animateToFinalPosition(value)
            }
        }
        // Animate alpha of clear all if translating grid to hide it.
        if (recentsView.isClearAllHidden) {
            SpringAnimation(
                    recentsView.clearAllButton,
                    FloatPropertyCompat.createFloatPropertyCompat(ClearAllButton.DISMISS_ALPHA),
                )
                .setSpring(createExpressiveDismissAlphaSpringForce())
                .addEndListener { _, _, _, _ -> recentsView.clearAllButton.dismissAlpha = 1f }
                .animateToFinalPosition(0f)
        }
        gridEndSpring.addEndListener { _, _, _, _ -> runGridEndAnimationAndRelayout(gridEndData) }
        gridEndSpring.animateToFinalPosition(gridEndOffset)
    }

    /** Returns the distance between the end of the grid and clear all button after dismissal. */
    fun getGridEndData(
        dismissedTaskView: TaskView?,
        isExpressiveDismiss: Boolean = true,
        isFocusedTaskDismissed: Boolean = false,
        nextFocusedTaskView: TaskView? = null,
        isStagingFocusedTask: Boolean = false,
        nextFocusedTaskFromTop: Boolean = false,
        nextFocusedTaskWidth: Float = 0f,
    ): GridEndData {
        var gridEndOffset = 0f
        var snapToLastTask = false
        var newClearAllShortTotalWidthTranslation: Float
        var currentPageSnapsToEndOfGrid: Boolean
        with(recentsView) {
            val lastGridTaskView = if (showAsGrid()) lastGridTaskView else null
            val currentPageScroll = getScrollForPage(currentPage)
            val lastGridTaskScroll = getScrollForPage(indexOfChild(lastGridTaskView))
            currentPageSnapsToEndOfGrid = currentPageScroll == lastGridTaskScroll
            var topGridRowCount = mTopRowIdSet.size()
            var bottomGridRowCount =
                taskViewCount - mTopRowIdSet.size() - mUtils.getLargeTileCount()
            val topRowLonger = topGridRowCount > bottomGridRowCount
            val bottomRowLonger = bottomGridRowCount > topGridRowCount
            val dismissedFromTop =
                dismissedTaskView != null && mTopRowIdSet.contains(dismissedTaskView.taskViewId)
            val dismissedFromBottom =
                dismissedTaskView != null && !dismissedFromTop && !dismissedTaskView.isLargeTile
            if (dismissedFromTop || (isFocusedTaskDismissed && nextFocusedTaskFromTop)) {
                topGridRowCount--
            }
            if (dismissedFromBottom || (isFocusedTaskDismissed && !nextFocusedTaskFromTop)) {
                bottomGridRowCount--
            }
            newClearAllShortTotalWidthTranslation =
                getNewClearAllShortTotalWidthTranslation(
                    topGridRowCount,
                    bottomGridRowCount,
                    isStagingFocusedTask,
                )
            val isLastGridTaskViewVisibleForDismiss =
                when {
                    lastGridTaskView == null -> false
                    isExpressiveDismiss ->
                        isTaskViewVisible(lastGridTaskView) || lastGridTaskView == dismissedTaskView
                    else -> lastGridTaskView.isVisibleToUser
                }
            if (!isLastGridTaskViewVisibleForDismiss) {
                return GridEndData(
                    gridEndOffset,
                    snapToLastTask,
                    newClearAllShortTotalWidthTranslation,
                    currentPageSnapsToEndOfGrid,
                )
            }
            val dismissedTaskWidth =
                if (dismissedTaskView == null) 0f
                else (dismissedTaskView.layoutParams.width + pageSpacing).toFloat()
            val gapWidth =
                when {
                    (topRowLonger && dismissedFromTop) ||
                        (bottomRowLonger && dismissedFromBottom) -> dismissedTaskWidth
                    nextFocusedTaskView != null &&
                        ((topRowLonger && nextFocusedTaskFromTop) ||
                            (bottomRowLonger && !nextFocusedTaskFromTop)) -> nextFocusedTaskWidth
                    else -> 0f
                }
            if (gapWidth > 0) {
                if (clearAllShortTotalWidthTranslation == 0) {
                    val gapCompensation = gapWidth - newClearAllShortTotalWidthTranslation
                    gridEndOffset += if (isRtl) -gapCompensation else gapCompensation
                }
                if (isClearAllHidden) {
                    // If ClearAllButton isn't fully shown, snap to the last task.
                    snapToLastTask = true
                }
            }
            val isLeftRightSplit =
                (mContainer as ActivityContext).getDeviceProfile().isLeftRightSplit &&
                    isSplitSelectionActive
            if (isLeftRightSplit && !isStagingFocusedTask) {
                // LastTask's scroll is the minimum scroll in split select, if current scroll is
                // beyond that, we'll need to snap to last task instead.
                getLastGridTaskView()?.let { lastTask ->
                    val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
                    val lastTaskScroll = getScrollForPage(indexOfChild(lastTask))
                    if (
                        (isRtl && primaryScroll < lastTaskScroll) ||
                            (!isRtl && primaryScroll > lastTaskScroll)
                    ) {
                        snapToLastTask = true
                    }
                }
            }
            if (snapToLastTask) {
                gridEndOffset += snapToLastTaskScrollDiff.toFloat()
            } else if (isLeftRightSplit && currentPageSnapsToEndOfGrid) {
                // Use last task as reference point for scroll diff and snapping calculation as it's
                // the only invariant point in landscape split screen.
                snapToLastTask = true
            }

            // Handle large tile scroll when dismissing the last small task.
            if (mUtils.getGridTaskCount() == 1 && dismissedTaskView?.isGridTask == true) {
                mUtils.getLastLargeTaskView()?.let { lastLargeTile ->
                    val primaryScroll = pagedOrientationHandler.getPrimaryScroll(this)
                    val lastLargeTileScroll = getScrollForPage(indexOfChild(lastLargeTile))
                    gridEndOffset = (primaryScroll - lastLargeTileScroll).toFloat()

                    if (!isClearAllHidden) {
                        // If ClearAllButton is visible, reduce the distance by scroll difference
                        // between ClearAllButton and the last task.
                        gridEndOffset +=
                            getLastTaskScroll(
                                    /*clearAllScroll=*/ 0,
                                    pagedOrientationHandler.getPrimarySize(clearAllButton),
                                )
                                .toFloat()
                    }
                }
            }
        }
        return GridEndData(
            gridEndOffset,
            snapToLastTask,
            newClearAllShortTotalWidthTranslation,
            currentPageSnapsToEndOfGrid,
        )
    }

    private fun getNewClearAllShortTotalWidthTranslation(
        topGridRowCount: Int,
        bottomGridRowCount: Int,
        isStagingFocusedTask: Boolean,
    ): Float {
        with(recentsView) {
            if (clearAllShortTotalWidthTranslation != 0) {
                return 0f
            }
            // If first task is not in the expected position (mLastComputedTaskSize) and too
            // close to ClearAllButton, then apply extra translation to ClearAllButton.
            var longRowWidth =
                max(topGridRowCount, bottomGridRowCount) *
                    (mLastComputedGridTaskSize.width() + pageSpacing)
            if (!enableGridOnlyOverview() && !isStagingFocusedTask) {
                longRowWidth += mLastComputedTaskSize.width() + pageSpacing
            }
            val firstTaskStart = mLastComputedGridSize.left + longRowWidth
            val expectedFirstTaskStart = mLastComputedTaskSize.right
            // Compensate the removed gap if we don't already have shortTotalCompensation,
            // and adjust accordingly to the new shortTotalCompensation after dismiss.
            return if (firstTaskStart < expectedFirstTaskStart) {
                (expectedFirstTaskStart - firstTaskStart).toFloat()
            } else {
                0f
            }
        }
    }

    data class GridEndData(
        val gridEndOffset: Float,
        val snapToLastTask: Boolean,
        val newClearAllShortTotalWidthTranslation: Float,
        val currentPageSnapsToEndOfGrid: Boolean,
    )

    private companion object {
        // The additional damping to apply to tasks further from the dismissed task.
        private const val ADDITIONAL_DISMISS_DAMPING_RATIO = 0.15f
        private const val RECENTS_SCALE_SPRING_MULTIPLIER = 1000f
        private const val DISMISS_DEFAULT_DURATION = 300
        private const val DISMISS_IMMEDIATE_DURATION = 100
        private const val DEFAULT_DISMISS_THRESHOLD_FRACTION = 0.5f
    }
}
