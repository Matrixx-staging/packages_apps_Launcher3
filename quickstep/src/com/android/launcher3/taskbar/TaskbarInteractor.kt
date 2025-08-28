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

package com.android.launcher3.taskbar

import android.animation.Animator
import android.animation.AnimatorSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewRootImpl
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.android.launcher3.Flags.enableTaskbarUiThread
import com.android.launcher3.LauncherState
import com.android.launcher3.taskbar.TaskbarManagerImpl.INSTANT_EXECUTOR
import com.android.launcher3.taskbar.TaskbarManagerImpl.TASKBAR_UI_THREAD
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator
import com.android.quickstep.GestureState
import com.android.quickstep.RecentsAnimationCallbacks
import javax.annotation.concurrent.ThreadSafe

/**
 * Expose [TaskbarUIController] APIs to launcher, gesture nav and recents to be called on taskbar's
 * per-window UI thread.
 */
@ThreadSafe
class TaskbarInteractor(private val taskbarUIController: TaskbarUIController) {

    private val executor = if (enableTaskbarUiThread()) TASKBAR_UI_THREAD else INSTANT_EXECUTOR

    @AnyThread
    fun setUserIsNotGoingHome(isNotGoingHome: Boolean) {
        executor.execute { taskbarUIController.setUserIsNotGoingHome(isNotGoingHome) }
    }

    @AnyThread
    fun hideOverlayWindow() {
        executor.execute { taskbarUIController.hideOverlayWindow() }
    }

    @AnyThread
    fun startTranslationSpring() {
        executor.execute { taskbarUIController.startTranslationSpring() }
    }

    @AnyThread
    fun onExpandPip() {
        executor.execute { taskbarUIController.onExpandPip() }
    }

    @AnyThread
    fun onLauncherVisibilityChanged(visible: Boolean) {
        executor.execute { taskbarUIController.onLauncherVisibilityChanged(visible) }
    }

    @AnyThread
    fun onStateTransitionCompletedAfterSwipeToHome(finalState: LauncherState) {
        executor.execute {
            taskbarUIController.onStateTransitionCompletedAfterSwipeToHome(finalState)
        }
    }

    @AnyThread
    fun notifyRenderer(reason: String) {
        executor.execute {
            val rootViewImpl: ViewRootImpl = taskbarUIController.rootView.viewRootImpl
            rootViewImpl.notifyRendererOfExpensiveFrame()
            rootViewImpl.notifyRendererForGpuLoadUp(reason)
        }
    }

    @AnyThread
    fun onTaskbarInAppDisplayProgressUpdate(progress: Float, flag: Int) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute {
                taskbarUIController.onTaskbarInAppDisplayProgressUpdate(progress, flag)
            }
        }
    }

    @AnyThread
    fun setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute {
                taskbarUIController.setShouldDelayLauncherStateAnim(shouldDelayLauncherStateAnim)
            }
        }
    }

    @AnyThread
    fun showEduOnAppLaunch() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.showEduOnAppLaunch() }
        }
    }

    @AnyThread
    fun openQuickSwitchView() {
        executor.execute { taskbarUIController.openQuickSwitchView() }
    }

    @AnyThread
    fun refreshResumedState() {
        executor.execute { taskbarUIController.refreshResumedState() }
    }

    @AnyThread
    fun setSkipLauncherVisibilityChange(skip: Boolean) {
        executor.execute { taskbarUIController.setSkipLauncherVisibilityChange(skip) }
    }

    @AnyThread
    fun onLauncherResume() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherResume() }
        }
    }

    @AnyThread
    fun onLauncherPause() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherPause() }
        }
    }

    @AnyThread
    fun onLauncherStop() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.onLauncherStop() }
        }
    }

    @AnyThread
    fun setIgnoreInAppFlagForSync(enabled: Boolean) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.setIgnoreInAppFlagForSync(enabled) }
        }
    }

    @AnyThread
    fun createAnimToAppAndPlay(animatorSet: AnimatorSet) {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.createAnimToApp().let { animatorSet.play(it) } }
        }
    }

    @AnyThread
    fun updateTaskbarLauncherStateGoingHome() {
        if (taskbarUIController is LauncherTaskbarUIController) {
            executor.execute { taskbarUIController.updateTaskbarLauncherStateGoingHome() }
        }
    }

    // TODO(b/404636836): expose focused task id to TaskbarUiState
    @MainThread fun launchFocusedTask(): Set<Int>? = taskbarUIController.launchFocusedTask()

    // TODO(b/404636836): refactor maxPinnableCount to TaskbarUiState
    @MainThread fun getRootView(): View = taskbarUIController.rootView

    // TODO(b/404636836): remove after revert ag/34711156
    @MainThread fun getControllers(): TaskbarControllers? = taskbarUIController.mControllers

    // TODO(b/404636836): Remove and expose maxPinnableCount from DeviceProfile
    @MainThread
    fun getTaskbarSpecsEvaluator(): TaskbarSpecsEvaluator =
        taskbarUIController.taskbarSpecsEvaluator

    // TODO(fengjial): refactor isTransient to TaskbarUiState
    @MainThread
    fun getTaskbarFeatureEvaluator(): TaskbarFeatureEvaluator =
        taskbarUIController.taskbarFeatureEvaluator

    // TODO(b/404636836): expose taskbar view rect and offset vai [TaskbarUiState]
    @MainThread
    fun isEventOverBubbleBarViews(ev: MotionEvent) =
        taskbarUIController.isEventOverBubbleBarViews(ev)

    // TODO(b/404636836): expose taskbar view rect and offset vai [TaskbarUiState]
    @MainThread
    fun isEventOverAnyTaskbarItem(ev: MotionEvent) =
        taskbarUIController.isEventOverAnyTaskbarItem(ev)

    // TODO(b/404636836): return AsyncView and post alpha and decor change to executor
    @MainThread fun findMatchingView(v: View) = taskbarUIController.findMatchingView(v)

    // TODO(b/404636836): start this animation within taskbar.
    @MainThread
    fun getParallelAnimationToGestureEndTarget(
        endTarget: GestureState.GestureEndTarget,
        duration: Long,
        callbacks: RecentsAnimationCallbacks,
    ): Animator? =
        taskbarUIController.getParallelAnimationToGestureEndTarget(endTarget, duration, callbacks)

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShowDesktopTaskbarForFreeformDisplayRef.value()"),
    )
    fun canPinAppWithContextMenu() = taskbarUIController.canPinAppWithContextMenu()

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getHasBubblesRef().value()"),
    )
    fun hasBubbles() =
        if (taskbarUIController is LauncherTaskbarUIController) {
            taskbarUIController.hasBubbles()
        } else null

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShouldShowEduOnAppLaunchRef().value()"),
    )
    fun shouldShowEduOnAppLaunch() =
        if (taskbarUIController is LauncherTaskbarUIController) {
            taskbarUIController.shouldShowEduOnAppLaunch()
        } else null

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isDraggingItemRef().value()"),
    )
    fun isDraggingItem() = taskbarUIController.isDraggingItem

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isTaskbarStashedRef().value()"),
    )
    fun isTaskbarStashed() = taskbarUIController.isTaskbarStashed

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.isTaskbarAllAppsOpenRef().value()"),
    )
    fun isTaskbarAllAppsOpen() = taskbarUIController.isTaskbarAllAppsOpen

    @Deprecated(
        "Should be removed once we turned on [refactorTaskbarUiState()] flag",
        ReplaceWith("TaskbarUiState.getShowDesktopTaskbarForFreeformDisplayRef().value()"),
    )
    fun shouldAllowTaskbarToAutoStash() = taskbarUIController.shouldAllowTaskbarToAutoStash()
}
