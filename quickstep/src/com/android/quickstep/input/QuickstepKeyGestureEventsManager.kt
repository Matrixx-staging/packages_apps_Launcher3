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
import android.content.Context
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.window.flags.Flags

/**
 * Manages subscription and unsubscription to launcher's key gesture events, e.g. all apps and
 * recents (incl. alt + tab).
 */
class QuickstepKeyGestureEventsManager(context: Context) {
    private val inputManager = requireNotNull(context.getSystemService(InputManager::class.java))
    private var allAppsPendingIntent: PendingIntent? = null
    @VisibleForTesting
    val allAppsKeyGestureEventHandler =
        object : KeyGestureEventHandler {
            override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
                if (!Flags.enableKeyGestureHandlerForRecents()) {
                    return
                }
                if (event.keyGestureType != KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS) {
                    Log.e(TAG, "Ignore unsupported key gesture event type: ${event.keyGestureType}")
                    return
                }

                // Ignore the display ID from the KeyGestureEvent as we will use the focus display
                // from the SysUi proxy as the source of truth.
                allAppsPendingIntent?.send()
            }
        }

    /** Registers the all apps key gesture events. */
    fun registerAllAppsKeyGestureEvent(allAppsPendingIntent: PendingIntent) {
        if (Flags.enableKeyGestureHandlerForRecents()) {
            this.allAppsPendingIntent = allAppsPendingIntent
            inputManager.registerKeyGestureEventHandler(
                listOf(KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS),
                allAppsKeyGestureEventHandler,
            )
        }
    }

    /** Unregisters the all apps key gesture events. */
    fun unregisterAllAppsKeyGestureEvent() {
        if (Flags.enableKeyGestureHandlerForRecents()) {
            inputManager.unregisterKeyGestureEventHandler(allAppsKeyGestureEventHandler)
        }
    }

    private companion object {
        const val TAG = "KeyGestureEventsHandler"
    }
}
