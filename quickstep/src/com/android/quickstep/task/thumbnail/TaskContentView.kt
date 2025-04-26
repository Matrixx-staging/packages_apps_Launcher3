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

package com.android.quickstep.task.thumbnail

import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewStub
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.android.launcher3.Flags.enableRefactorDigitalWellbeingToast
import com.android.launcher3.R
import com.android.launcher3.util.ViewPool
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.apptimer.TimerTextHelper
import com.android.quickstep.util.setActivityStarterClickListener
import com.android.quickstep.views.TaskHeaderView

/**
 * TaskContentView is a wrapper around the TaskHeaderView, TaskThumbnailView and Digital wellbeing
 * app timer toast. It is a sibling to AiAi (TaskOverlay).
 *
 * When enableRefactorDigitalWellbeingToast is off, it is sibling to digital wellbeing toast unlike
 * when the flag is on.
 */
class TaskContentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs), ViewPool.Reusable {

    private var taskHeaderView: TaskHeaderView? = null
    private var taskThumbnailView: TaskThumbnailView? = null
    private var taskAppTimerToast: TextView? = null

    private var timerTextHelper: TimerTextHelper? = null
    private var timerUiState: TaskAppTimerUiState = TaskAppTimerUiState.Uninitialized
    private var timerUsageAccessibilityAction: AccessibilityAction? = null
    private val timerToastHeight =
        context.resources.getDimensionPixelSize(R.dimen.digital_wellbeing_toast_height)

    private var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null
    private val outlinePath = Path()

    /**
     * Sets the outline bounds of the view. Default to use view's bound as outline when set to null.
     */
    var outlineBounds: Rect? = null
        set(value) {
            field = value
            invalidateOutline()
        }

    private val bounds = Rect()

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        createTaskThumbnailView()
    }

    override fun setScaleX(scaleX: Float) {
        super.setScaleX(scaleX)
        taskThumbnailView?.parentScaleXUpdated(scaleX)
    }

    override fun setScaleY(scaleY: Float) {
        super.setScaleY(scaleY)
        taskThumbnailView?.parentScaleYUpdated(scaleY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val outlineRect = outlineBounds ?: bounds
                    outlinePath.apply {
                        rewind()
                        addRoundRect(
                            outlineRect.left.toFloat(),
                            outlineRect.top.toFloat(),
                            outlineRect.right.toFloat(),
                            outlineRect.bottom.toFloat(),
                            cornerRadius / scaleX,
                            cornerRadius / scaleY,
                            Path.Direction.CW,
                        )
                    }
                    outline.setPath(outlinePath)
                }
            }
    }

    override fun onRecycle() {
        taskHeaderView?.isInvisible = true
        onSizeChanged = null
        outlineBounds = null
        alpha = 1.0f
        taskThumbnailView?.onRecycle()
        taskAppTimerToast?.isInvisible = true
        timerUiState = TaskAppTimerUiState.Uninitialized
        timerTextHelper = null
        timerUsageAccessibilityAction = null
    }

    fun doOnSizeChange(action: (width: Int, height: Int) -> Unit) {
        onSizeChanged = action
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        onSizeChanged?.invoke(width, height)
        bounds.set(0, 0, w, h)
        updateTimerText(w)
        invalidateOutline()
    }

    fun onParentAnimationProgress(progress: Float) {
        taskAppTimerToast?.apply { translationY = timerToastHeight * (1f - progress) }
    }

    /** Returns accessibility actions supported by items in the task content view. */
    fun getSupportedAccessibilityActions(): List<AccessibilityAction> {
        return listOfNotNull(timerUsageAccessibilityAction)
    }

    fun handleAccessibilityAction(action: Int): Boolean {
        timerUsageAccessibilityAction?.let {
            if (action == it.id) {
                return taskAppTimerToast?.callOnClick() ?: false
            }
        }

        return false
    }

    private fun createHeaderView(taskHeaderState: TaskHeaderUiState) {
        if (taskHeaderView == null && taskHeaderState is TaskHeaderUiState.ShowHeader) {
            taskHeaderView =
                findViewById<ViewStub>(R.id.task_header_view)
                    .apply { layoutResource = R.layout.task_header_view }
                    .inflate() as TaskHeaderView

            // TODO: Move to layout xml when moving away from view stubs.
            val constraintSet = ConstraintSet().apply { clone(this@TaskContentView) }
            constraintSet.connect(
                R.id.snapshot,
                ConstraintSet.TOP,
                R.id.task_header_view,
                ConstraintSet.BOTTOM,
            )
            constraintSet.applyTo(this@TaskContentView)
        }
    }

    private fun createTaskThumbnailView() {
        if (taskThumbnailView == null) {
            taskThumbnailView =
                findViewById<ViewStub>(R.id.snapshot)
                    .apply { layoutResource = R.layout.task_thumbnail }
                    .inflate() as TaskThumbnailView
        }
    }

    private fun createAppTimerToastView(taskAppTimerUiState: TaskAppTimerUiState) {
        if (
            enableRefactorDigitalWellbeingToast() &&
                taskAppTimerToast == null &&
                taskAppTimerUiState is TaskAppTimerUiState.Timer
        ) {
            taskAppTimerToast =
                findViewById<ViewStub>(R.id.task_app_timer_toast)
                    .apply { layoutResource = R.layout.task_app_timer_toast }
                    .inflate() as TextView
        }
    }

    fun setState(
        taskHeaderState: TaskHeaderUiState,
        taskThumbnailUiState: TaskThumbnailUiState,
        taskAppTimerUiState: TaskAppTimerUiState,
        taskId: Int?,
    ) {
        createHeaderView(taskHeaderState)
        taskHeaderView?.setState(taskHeaderState)
        taskThumbnailView?.setState(taskThumbnailUiState, taskId)
        createAppTimerToastView(taskAppTimerUiState)
        if (enableRefactorDigitalWellbeingToast() && timerUiState != taskAppTimerUiState) {
            setAppTimerToastState(taskAppTimerUiState)
            updateContentDescriptionWithTimer(taskAppTimerUiState)
        }
    }

    private fun updateContentDescriptionWithTimer(state: TaskAppTimerUiState) {
        taskThumbnailView?.contentDescription =
            when (state) {
                is TaskAppTimerUiState.Uninitialized -> return
                is TaskAppTimerUiState.NoTimer -> state.taskDescription
                is TaskAppTimerUiState.Timer ->
                    timerTextHelper?.let {
                        context.getString(
                            R.string.task_contents_description_with_remaining_time,
                            state.taskDescription,
                            context.getString(R.string.time_left_for_app, it.formattedDuration),
                        )
                    }
            }
    }

    private fun setAppTimerToastState(state: TaskAppTimerUiState) {
        timerUiState = state

        taskAppTimerToast?.apply {
            when (state) {
                is TaskAppTimerUiState.Uninitialized -> isInvisible = true
                is TaskAppTimerUiState.NoTimer -> isInvisible = true
                is TaskAppTimerUiState.Timer -> {
                    timerTextHelper = TimerTextHelper(context, state.timeRemaining)
                    isInvisible = false
                    updateTimerText(width)

                    // TODO: add WW logging on the app usage settings click.
                    setActivityStarterClickListener(
                        appUsageSettingsIntent(state.taskPackageName),
                        "app usage settings for task ${state.taskDescription}",
                    )

                    timerUsageAccessibilityAction =
                        appUsageSettingsAccessibilityAction(
                            context,
                            state.accessibilityActionId,
                            state.taskDescription,
                        )
                }
            }
        }
    }

    private fun updateTimerText(width: Int) {
        taskAppTimerToast?.apply {
            val helper = timerTextHelper

            if (isVisible && helper != null) {
                text = helper.getTextThatFits(width, paint)
            }
        }
    }

    companion object {
        const val TAG = "TaskContentView"

        private fun appUsageSettingsIntent(packageName: String) =
            Intent(Intent(Settings.ACTION_APP_USAGE_SETTINGS))
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        private fun appUsageSettingsAccessibilityAction(
            context: Context,
            @IdRes actionId: Int,
            taskDescription: String?,
        ) =
            AccessibilityAction(
                actionId,
                context.getString(R.string.split_app_usage_settings, taskDescription),
            )
    }
}
