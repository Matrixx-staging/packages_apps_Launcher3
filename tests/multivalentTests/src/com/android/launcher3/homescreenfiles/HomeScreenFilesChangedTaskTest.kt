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

package com.android.launcher3.homescreenfiles

import android.content.ContentResolver
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesChangedTaskTest {
    @Mock private lateinit var modelTaskController: ModelTaskController
    @Mock private lateinit var launcherModel: LauncherModel
    @Mock private lateinit var bgDataModel: BgDataModel
    @Mock private lateinit var allAppsList: AllAppsList

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(modelTaskController.model).thenReturn(launcherModel)
    }

    @Test
    fun testExecutesTask() {
        val task =
            HomeScreenFilesChangedTask(
                Uri.parse("content://media/external/file/1"),
                ContentResolver.NOTIFY_INSERT,
            )
        task.execute(modelTaskController, bgDataModel, allAppsList)
        verify(modelTaskController, times(1)).deleteAndBindComponentsRemoved(any(), any())
        verify(launcherModel, times(1)).forceReload()
    }
}
