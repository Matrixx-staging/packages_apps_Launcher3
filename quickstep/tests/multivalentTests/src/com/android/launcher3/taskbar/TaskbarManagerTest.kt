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

import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
class TaskbarManagerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    private val taskbarManager by taskbarUnitTestRule::taskbarManager

    @Test
    fun addDisplay_externalActivityContextInitialized() {
        val displayId = context.virtualDisplayRule.add()
        val activityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))

        assertThat(activityContext.displayId).isEqualTo(displayId)
        // Allow drag layer to attach before checking.
        runOnMainSync { assertThat(activityContext.dragLayer.isAttachedToWindow).isTrue() }
    }

    @Test
    fun removeDisplay_externalActivityContextDestroyed() {
        val displayId = context.virtualDisplayRule.add()
        val activityContext = checkNotNull(taskbarManager.getTaskbarForDisplay(displayId))
        context.virtualDisplayRule.remove(displayId)

        assertThat(taskbarManager.getTaskbarForDisplay(displayId)).isNull()
        assertThat(activityContext.dragLayer.isAttachedToWindow).isFalse()
        assertThat(activityContext.isDestroyed).isTrue()
    }
}
