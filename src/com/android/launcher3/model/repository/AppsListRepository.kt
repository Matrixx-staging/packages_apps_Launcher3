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

package com.android.launcher3.model.repository

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.AppsListData
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Repository for app-list daya. */
@LauncherAppSingleton
class AppsListRepository(private val scope: CoroutineScope) {

    @Inject constructor() : this(CoroutineScope(MODEL_EXECUTOR.asCoroutineDispatcher()))

    private val mutableStateFlow: MutableStateFlow<AppsListData> =
        MutableStateFlow(AppsListData(emptyArray(), 0))

    /** Represents the current home screen data model. There are two ways this can change: */
    val appsListStateFlow = mutableStateFlow.asStateFlow()

    /** sets a new value to [appsListStateFlow] */
    fun dispatchChange(appsListData: AppsListData) {
        mutableStateFlow.value = appsListData
    }

    private val mutableIncrementalUpdate = MutableSharedFlow<AppInfo>()
    /** Represents incremental download apps to apps list items */
    val incrementalUpdates = mutableIncrementalUpdate.asSharedFlow()

    /** Dispatches an incremental download update to [incrementalUpdates] */
    fun dispatchIncrementationUpdate(appInfo: AppInfo) {
        scope.launch { mutableIncrementalUpdate.emit(appInfo) }
    }
}
