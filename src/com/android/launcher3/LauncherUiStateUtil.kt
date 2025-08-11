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

import com.android.launcher3.Flags.refactorTaskbarUiState

object LauncherUiStateUtil {

    @JvmStatic
    fun getDeviceProfile(launcher: Launcher, launcherUiState: LauncherUiState): DeviceProfile {
        if (refactorTaskbarUiState()) {
            val ret: DeviceProfile = launcherUiState.deviceProfileRef.value!!
            check(!(BuildConfig.IS_STUDIO_BUILD && ret !== launcher.getDeviceProfile())) {
                "getDeviceProfile() doesn't match"
            }
            return ret
        } else {
            return launcher.getDeviceProfile()
        }
    }

    @JvmStatic
    fun isSplitSelectActive(launcher: Launcher, launcherUiState: LauncherUiState): Boolean {
        if (refactorTaskbarUiState()) {
            val ret: Boolean = launcherUiState.isSplitSelectActiveRef.value
            if (BuildConfig.IS_STUDIO_BUILD && ret != launcher.isSplitSelectionActive) {
                throw IllegalStateException("isSplitSelectionActive() doesn't match")
            }
            return ret
        } else {
            return launcher.isSplitSelectionActive
        }
    }
}
