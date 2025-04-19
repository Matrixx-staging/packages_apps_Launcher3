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

package com.android.quickstep.input

import android.app.PendingIntent
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SandboxApplication
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickstepKeyGestureEventsHandlerTest {
    @get:Rule val context = SandboxApplication()

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val inputManager = context.spyService(InputManager::class.java)
    private val keyGestureEventsManager = QuickstepKeyGestureEventsManager(context)
    private val allAppsPendingIntent: PendingIntent = mock()
    private val keyGestureEventsCaptor: KArgumentCaptor<List<Int>> = argumentCaptor()

    @Before
    fun setup() {
        doNothing().whenever(inputManager).registerKeyGestureEventHandler(any(), any())
        doNothing().whenever(inputManager).unregisterKeyGestureEventHandler(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun registerKeyGestureEventsHandler_flagEnabled_registerWithExpectedKeyGestureEvents() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.allAppsKeyGestureEventHandler),
            )
        assertThat(keyGestureEventsCaptor.firstValue).containsExactly(KEY_GESTURE_TYPE_ALL_APPS)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun registerKeyGestureEventsHandler_flagDisabled_noRegister() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun unregisterKeyGestureEventsHandler_flagEnabled_unregisterHandler() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()

        verify(inputManager)
            .unregisterKeyGestureEventHandler(
                eq(keyGestureEventsManager.allAppsKeyGestureEventHandler)
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun unregisterKeyGestureEventsHandler_flagDisabled_noUnregister() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun handleEvent_flagEnabled_allApps_toggleAllAppsSearch() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        keyGestureEventsManager.allAppsKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verify(allAppsPendingIntent).send()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_RECENTS)
    fun handleEvent_flagDisabled_allApps_noInteractionWithTaskbar() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        keyGestureEventsManager.allAppsKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    private companion object {
        const val TEST_DISPLAY_ID = 6789
    }
}
