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

package com.android.quickstep.task.apptimer

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ViewModel<T> {
    val uiState: StateFlow<T>
}

class TaskAppTimerViewModel : ViewModel<TaskAppTimerUiState> {
    private val _appTimerUiState =
        MutableStateFlow<TaskAppTimerUiState>(TaskAppTimerUiState.Uninitialized)

    override val uiState = _appTimerUiState.asStateFlow()

    private fun startActivityWithScaleUpAnimation(
        packageName: String,
        description: String?,
        options: ActivityOptions,
        context: Context,
    ) {
        try {
            context.startActivity(appUsageSettingsIntent(packageName), options.toBundle())
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Failed to open $description ", e)
        }
    }

    fun setState(uiState: TaskAppTimerUiState) {
        _appTimerUiState.value =
            if (uiState is TaskAppTimerUiState.Timer)
                uiState.copy { activityOptions, context ->
                    startActivityWithScaleUpAnimation(
                        uiState.taskPackageName,
                        uiState.taskDescription,
                        activityOptions,
                        context,
                    )
                }
            else {
                uiState
            }
    }

    private fun appUsageSettingsIntent(packageName: String) =
        Intent(Settings.ACTION_APP_USAGE_SETTINGS)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private companion object {
        const val TAG = "TaskAppTimerViewModel"
    }
}
