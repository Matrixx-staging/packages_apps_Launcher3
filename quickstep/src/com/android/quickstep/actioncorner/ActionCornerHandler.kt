/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.quickstep.actioncorner

import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.OverviewCommandHelper.CommandType.TOGGLE_OVERVIEW_PREVIOUS
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.Action
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW

/**
 * Handles actions triggered from action corners that are mapped to specific functionalities.
 * Launcher supports both overview and home actions.
 */
class ActionCornerHandler(private val overviewCommandHelper: OverviewCommandHelper) {

    fun handleAction(@Action action: Int, displayId: Int) {
        when (action) {
            // TODO(b/410798748): handle projected mode when launching overview
            OVERVIEW -> overviewCommandHelper.addCommandsForAllDisplays(TOGGLE_OVERVIEW_PREVIOUS)
            HOME -> {} // TODO(b/409036363): handle HOME action
            else -> {}
        }
    }
}
