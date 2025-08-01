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

package com.android.launcher3

import com.android.launcher3.util.MutableListenableRef

/** Expose Launcher Ui State to Taskbar. */
class LauncherUiState {

    private val _isResumedRef = MutableListenableRef(false)
    private val _deviceProfileRef = MutableListenableRef<DeviceProfile?>(null)

    val isResumedRef = _isResumedRef.asListenable()
    val deviceProfileRef = _deviceProfileRef.asListenable()

    fun setIsResumes(isResumed: Boolean) {
        _isResumedRef.diffAndDispatch(isResumed)
    }

    fun setDeviceProfile(deviceProfile: DeviceProfile) {
        _deviceProfileRef.diffAndDispatch(deviceProfile)
    }

    private fun <T> MutableListenableRef<T>.diffAndDispatch(newValue: T) {
        if (value != newValue) {
            dispatchValue(newValue)
        }
    }
}
