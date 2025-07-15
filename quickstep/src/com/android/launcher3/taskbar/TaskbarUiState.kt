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

import com.android.launcher3.util.MutableListenableRef

/**
 * Data class that represents taskbar's UI states. This state is shared to launcher and recents.
 * Taskbar's UI thread is responsible to update below fields whenever any field is changed.
 *
 * Timings when each field is changed:
 * - [_hasBubblesRef]: when BubbleBarView's child bubble view count is changed between 0 vs non-zero
 * - [_shouldShowEduOnAppLaunchRef]: when DeviceProfile or tooltip steps is changed
 * - [_isDraggingItemRef]: when ether bubble or taskbar is dragging item
 * - [_isTaskbarStashedRef]: when [TaskbarStashController.mIsStashed] has changed
 * - [_isTaskbarAllAppsOpenRef]: when [TaskbarAllAppsController.isOpen] has changed
 * - [_isTaskbarOnHomeRef]: when [TaskbarStashController.mState] is changed
 * - [_showDesktopTaskbarForFreeformDisplayRef]: when [DisplayInfo] is changed
 * - [_showLockedTaskbarOnHome]: when [DisplayInfo] is changed
 * - [_isPrimaryDisplayRef]: when [TaskbarActivityContext] is constructed
 */
class TaskbarUiState {

    private val _hasBubblesRef = MutableListenableRef(false)
    private val _shouldShowEduOnAppLaunchRef = MutableListenableRef(false)
    private val _isDraggingItemRef = MutableListenableRef(false)
    private val _isTaskbarStashedRef = MutableListenableRef(false)
    private val _isTaskbarAllAppsOpenRef = MutableListenableRef(false)
    private val _isTaskbarOnHomeRef = MutableListenableRef(false)
    private val _showDesktopTaskbarForFreeformDisplayRef = MutableListenableRef(false)
    private val _showLockedTaskbarOnHome = MutableListenableRef(false)
    private val _isPrimaryDisplayRef = MutableListenableRef(false)

    private fun <T> MutableListenableRef<T>.diffAndDispatch(newValue: T) {
        if (value != newValue) {
            dispatchValue(newValue)
        }
    }

    val hasBubblesRef = _hasBubblesRef.asListenable()
    val shouldShowEduOnAppLaunchRef = _shouldShowEduOnAppLaunchRef.asListenable()
    val isDraggingItemRef = _isDraggingItemRef.asListenable()
    val isTaskbarStashedRef = _isTaskbarStashedRef.asListenable()
    val isTaskbarAllAppsOpenRef = _isTaskbarAllAppsOpenRef.asListenable()
    val isTaskbarOnHomeRef = _isTaskbarOnHomeRef.asListenable()
    val showDesktopTaskbarForFreeformDisplayRef =
        _showDesktopTaskbarForFreeformDisplayRef.asListenable()
    val showLockedTaskbarOnHome = _showLockedTaskbarOnHome.asListenable()
    val isPrimaryDisplayRef = _isPrimaryDisplayRef.asListenable()

    private var _isBubbleDragging = false
    private var _isTaskbarDragging = false
    private var _stashState = 0L

    fun setHasBubble(hasBubbles: Boolean) {
        _hasBubblesRef.diffAndDispatch(hasBubbles)
    }

    fun setShouldShowEduOnAppLaunch(shouldShowEduOnAppLaunch: Boolean) {
        _shouldShowEduOnAppLaunchRef.diffAndDispatch(shouldShowEduOnAppLaunch)
    }

    fun setIsBubbleDragging(isBubbleDragging: Boolean) {
        _isBubbleDragging = isBubbleDragging
        _isDraggingItemRef.diffAndDispatch(_isBubbleDragging or _isTaskbarDragging)
    }

    fun setIsTaskbarDragging(isTaskbarDragging: Boolean) {
        _isTaskbarDragging = isTaskbarDragging
        _isDraggingItemRef.diffAndDispatch(_isBubbleDragging or _isTaskbarDragging)
    }

    fun setIsTaskbarStashed(isTaskbarStashed: Boolean) {
        _isTaskbarStashedRef.diffAndDispatch(isTaskbarStashed)
    }

    fun setIsTaskbarAllAppsOpen(isTaskbarAllAppsOpen: Boolean) {
        _isTaskbarAllAppsOpenRef.diffAndDispatch(isTaskbarAllAppsOpen)
    }

    fun setStashStateRef(state: Long) {
        _stashState = state
        _isTaskbarOnHomeRef.diffAndDispatch(
            (_stashState and TaskbarStashController.FLAG_IN_OVERVIEW.toLong()) == 0L &&
                (_stashState and TaskbarStashController.FLAG_IN_APP.toLong()) == 0L
        )
    }

    fun setShowDesktopTaskbarForFreeformDisplay(showDesktopTaskbarForFreeformDisplay: Boolean) {
        _showDesktopTaskbarForFreeformDisplayRef.diffAndDispatch(
            showDesktopTaskbarForFreeformDisplay
        )
    }

    fun setShowLockedTaskbarOnHome(showLockedTaskbarOnHome: Boolean) {
        _showLockedTaskbarOnHome.diffAndDispatch(showLockedTaskbarOnHome)
    }

    fun setIsPrimaryDisplay(isPrimaryDisplay: Boolean) {
        _isPrimaryDisplayRef.diffAndDispatch(isPrimaryDisplay)
    }
}
