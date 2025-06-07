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

package com.android.launcher3.widgetpicker.listeners

import android.appwidget.AppWidgetProviderInfo
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.util.ContextTracker.SchedulerCallback
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.PendingAddWidgetInfo

/**
 * A callback listener (for tap-to-add flow) that handles adding a widget from a separate widget
 * picker activity. Invoked once widget picker is closed and home screen is showing / ready.
 *
 * Also logs to stats logger once widget is added.
 */
class WidgetPickerAddItemListener(private val providerInfo: AppWidgetProviderInfo) :
    SchedulerCallback<Launcher> {
    override fun init(launcher: Launcher?, isHomeStarted: Boolean): Boolean {
        checkNotNull(launcher)

        val launcherProviderInfo =
            LauncherAppWidgetProviderInfo.fromProviderInfo(launcher, providerInfo)
        val pendingAddWidgetInfo =
            PendingAddWidgetInfo(launcherProviderInfo, CONTAINER_WIDGETS_TRAY)

        val view = View(launcher)
        view.tag = pendingAddWidgetInfo

        launcher.accessibilityDelegate?.addToWorkspace(
            /*item=*/ pendingAddWidgetInfo,
            /*accessibility=*/ false
        ) {
            launcher.statsLogManager
                .logger()
                .withItemInfo(pendingAddWidgetInfo)
                .log(LauncherEvent.LAUNCHER_WIDGET_ADD_BUTTON_TAP)
        }
        return false // don't receive any more callbacks as we got launcher and handled it
    }
}
