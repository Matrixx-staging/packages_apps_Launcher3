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

package com.android.launcher3.integration.dragndrop

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragEvent
import android.view.DragEvent.ACTION_DRAG_LOCATION
import android.view.DragEvent.ACTION_DRAG_STARTED
import android.view.DragEvent.ACTION_DROP
import android.view.View
import androidx.test.filters.LargeTest
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.Flags.enableSystemDrag
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerImpl
import com.android.launcher3.dragndrop.SystemDragControllerStub
import com.android.launcher3.dragndrop.SystemDragListener
import com.android.launcher3.util.BaseLauncherActivityTest
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.util.ReflectionHelpers
import com.android.launcher3.util.Wait.atMost
import com.android.launcher3.util.workspace.FavoriteItemsTransaction
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/** Integration tests for system-level drag-and-drop. */
@LargeTest
@RunWith(ParameterizedAndroidJunit4::class)
class SystemDragIntegrationTest(flags: FlagsParameterization, private val item: ClipData.Item) :
    BaseLauncherActivityTest<Launcher>() {

    companion object {
        @JvmStatic
        @Parameters(name = "flags={0}, item={1}")
        fun getParameters() = run {
            val flags = FlagsParameterization.allCombinationsOf(FLAG_ENABLE_SYSTEM_DRAG)
            val items = listOf(ClipData.Item("Text"), ClipData.Item(Uri.EMPTY))
            flags.flatMap { f -> items.map { i -> arrayOf(f, i) } }
        }
    }

    @get:Rule val flags = SetFlagsRule(flags)

    private lateinit var systemDragController: SystemDragController

    @Before
    fun setUp() {
        val context = targetContext()
        val iconCache = LauncherAppState.INSTANCE[context].iconCache

        // NOTE: Because the system drag controller is application scoped and the application
        // instance is reused across tests, we need to manually ensure that the system drag
        // controller instance is synced with parameterized flag state.
        SystemDragController.INSTANCE_FOR_TESTING =
            if (enableSystemDrag())
                SystemDragControllerImpl { launcher -> SystemDragListener(launcher) { iconCache } }
            else SystemDragControllerStub()

        FavoriteItemsTransaction(context).commit()
        loadLauncherSync()
    }

    @After
    fun tearDown() {
        SystemDragController.INSTANCE_FOR_TESTING = null
    }

    @Test
    fun testDragAndDropConditionallyCreatesWorkspaceItem() {
        launcherActivity.executeOnLauncher { launcher ->

            // Simulate a system-level drag-and-drop sequence.
            val start = PointF()
            val end = launcher.workspace.getExactCenterPointOnScreen()
            val description = ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf("*/*"))
            launcher.dragLayer.dispatchDragAndDropSequence(start, end, description, listOf(item))

            // Expect a workspace item to be created if and only if:
            // (a) the system-level drag controller is implemented, and
            // (b) the dropped payload contained a URI.
            val expectWorkspaceItemCreated =
                SystemDragController.INSTANCE[launcher] is SystemDragControllerImpl &&
                    item.uri != null

            // Verify workspace item creation (or lack thereof).
            val operator = ItemOperator { item, _ -> item?.itemType == ITEM_TYPE_FILE_SYSTEM_FILE }
            val condition = { launcher.workspace.mapOverItems(operator) != null }
            assertThrowsIf(
                "Workspace item created",
                { atMost("Workspace item not created", condition, timeout = SECONDS.toMillis(5)) },
                !expectWorkspaceItemCreated,
            )
        }
    }

    private fun assertThrows(message: String, block: () -> Unit) {
        assertThrows(message, AssertionError::class.java, block)
    }

    private fun assertThrowsIf(message: String, block: () -> Unit, condition: Boolean) {
        if (condition) {
            assertThrows(message, block)
            return
        }
        block()
    }

    private fun obtainDragEvent(
        action: Int,
        point: PointF,
        description: ClipDescription,
        items: List<ClipData.Item>? = null,
    ): DragEvent {
        val mockDragEvent = mock<DragEvent>()

        // NOTE: Reflection is necessary because `ViewGroup` inspects the `DragEvent.mAction` field
        // during event dispatching rather than using the mockable `DragEvent.getAction()` method.
        ReflectionHelpers.setField(mockDragEvent, "mAction", action)

        whenever(mockDragEvent.action).thenReturn(action)
        whenever(mockDragEvent.clipDescription).thenReturn(description)
        whenever(mockDragEvent.x).thenReturn(point.x)
        whenever(mockDragEvent.y).thenReturn(point.y)

        // NOTE: In production, clip data is only available during `ACTION_DROP` events.
        // See https://developer.android.com/reference/android/view/DragEvent.
        if (action == ACTION_DROP) {
            val item = if (items?.isEmpty() == false) items.first() else null
            val data = ClipData(description, item).apply { items?.drop(1)?.forEach(this::addItem) }
            whenever(mockDragEvent.clipData).thenReturn(data)
        }

        return mockDragEvent
    }

    private fun View.dispatchDragAndDropSequence(
        start: PointF,
        end: PointF,
        description: ClipDescription,
        items: List<ClipData.Item>,
    ) {
        val midpoint = PointF((start.x + end.x) / 2.0f, (start.y + end.y) / 2.0f)
        dispatchDragEvent(obtainDragEvent(ACTION_DRAG_STARTED, start, description))
        dispatchDragEvent(obtainDragEvent(ACTION_DRAG_LOCATION, midpoint, description))
        dispatchDragEvent(obtainDragEvent(ACTION_DROP, end, description, items))
    }

    private fun View.getExactCenterPointOnScreen() =
        Rect().apply(this::getBoundsOnScreen).let { PointF(it.exactCenterX(), it.exactCenterY()) }
}
