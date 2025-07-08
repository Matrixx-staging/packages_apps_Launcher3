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

package com.android.quickstep.recents.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.drawable.ShapeDrawable
import android.hardware.HardwareBuffer
import android.view.WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.TestDispatcherProvider
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.quickstep.recents.domain.model.TaskModel
import com.android.quickstep.recents.domain.usecase.GetSysUiStatusNavFlagsUseCase
import com.android.quickstep.recents.domain.usecase.GetTaskUseCase
import com.android.quickstep.recents.domain.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.domain.usecase.IsThumbnailValidUseCase
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaskViewModelOnDeviceTest {
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(unconfinedTestDispatcher)

    private val recentsViewData = RecentsViewData()
    private val getTaskUseCase = mock<GetTaskUseCase>()
    private val getThumbnailPositionUseCase = mock<GetThumbnailPositionUseCase>()
    private val isThumbnailValidUseCase =
        spy(IsThumbnailValidUseCase(FakeRecentsRotationStateRepository()))
    private lateinit var sut: TaskViewModel

    @Before
    fun setUp() {
        sut = createTaskViewModel()
        whenever(getTaskUseCase.invoke(TASK_MODEL_WITH_HARDWARE_THUMBNAIL.id))
            .thenReturn(flow { emit(TASK_MODEL_WITH_HARDWARE_THUMBNAIL) })
        whenever(getTaskUseCase.invoke(TASK_MODEL_WITH_SOFTWARE_THUMBNAIL.id))
            .thenReturn(flow { emit(TASK_MODEL_WITH_SOFTWARE_THUMBNAIL) })
        recentsViewData.runningTaskIds.value = emptySet()
    }

    @Test
    fun singleTaskRetrievedWithHardwareThumbnail_densityIsCleared() =
        testScope.runTest {
            sut.bind(TASK_MODEL_WITH_HARDWARE_THUMBNAIL.id)
            val actualResult = sut.state.first()
            val thumbnail =
                (actualResult.tasks.single() as? TaskData.Data)?.thumbnailData?.thumbnail
            assertThat(thumbnail).isNotNull()
            assertThat(thumbnail!!.hardwareBuffer).isEqualTo(HARDWARE_BITMAP.hardwareBuffer)
            assertThat(thumbnail.density).isEqualTo(Bitmap.DENSITY_NONE)
        }

    @Test
    fun singleTaskRetrievedWithSoftwareThumbnail_originalBitmapIsUsed() =
        testScope.runTest {
            sut.bind(TASK_MODEL_WITH_SOFTWARE_THUMBNAIL.id)
            val actualResult = sut.state.first()
            val thumbnail =
                (actualResult.tasks.single() as? TaskData.Data)?.thumbnailData?.thumbnail
            assertThat(thumbnail).isEqualTo(SOFTWARE_BITMAP)
        }

    private fun createTaskViewModel() =
        TaskViewModel(
            taskViewType = TaskViewType.SINGLE,
            recentsViewData = recentsViewData,
            getTaskUseCase = getTaskUseCase,
            getSysUiStatusNavFlagsUseCase = GetSysUiStatusNavFlagsUseCase(),
            isThumbnailValidUseCase = isThumbnailValidUseCase,
            getThumbnailPositionUseCase = getThumbnailPositionUseCase,
            dispatcherProvider = TestDispatcherProvider(unconfinedTestDispatcher),
        )

    private companion object {
        const val PACKAGE_NAME = "com.test"
        const val APPEARANCE_LIGHT_THEME =
            APPEARANCE_LIGHT_CAPTION_BARS or
                APPEARANCE_LIGHT_STATUS_BARS or
                APPEARANCE_LIGHT_NAVIGATION_BARS

        val HARDWARE_BITMAP =
            HardwareBuffer.create(
                    /* width= */ 128,
                    /* height= */ 128,
                    HardwareBuffer.RGBA_8888,
                    1,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
                )
                .use { hardwareBuffer ->
                    Bitmap.wrapHardwareBuffer(hardwareBuffer, ColorSpace.get(ColorSpace.Named.SRGB))
                }!!
        val TASK_MODEL_WITH_HARDWARE_THUMBNAIL =
            TaskModel(
                1,
                PACKAGE_NAME,
                "Title 1",
                "Content Description 1",
                ShapeDrawable(),
                ThumbnailData(appearance = APPEARANCE_LIGHT_THEME, thumbnail = HARDWARE_BITMAP),
                Color.BLUE,
                isLocked = false,
                isMinimized = true,
                remainingAppDuration = null,
            )

        val SOFTWARE_BITMAP = Bitmap.createBitmap(128, 128, ARGB_8888)
        val TASK_MODEL_WITH_SOFTWARE_THUMBNAIL =
            TaskModel(
                2,
                PACKAGE_NAME,
                "Title 2",
                "Content Description 2",
                ShapeDrawable(),
                ThumbnailData(appearance = APPEARANCE_LIGHT_THEME, thumbnail = SOFTWARE_BITMAP),
                Color.BLUE,
                isLocked = false,
                isMinimized = true,
                remainingAppDuration = null,
            )
    }
}
