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

package com.android.launcher3.qsb

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_PACKAGE_ADDED
import android.content.Intent.ACTION_PACKAGE_CHANGED
import android.content.Intent.ACTION_PACKAGE_REMOVED
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.SecureStringObserver
import com.android.launcher3.util.SimpleBroadcastReceiver
import javax.inject.Inject

/**
 * Manager to handle when OnDevice Search Engine selection changes.
 *
 * Listens to Settings.Secure and PackageManager.
 */
@LauncherAppSingleton
class OSEManager(
    private val context: Context,
    private val settingsObserver: SecureStringObserver,
    private val handlerLooper: Looper = OSE_LOOPER,
) {

    private val handler = Handler(handlerLooper)
    private val packageAvailableReceiver = SimpleBroadcastReceiver(context, handler) { reloadOse() }

    private val mutableOSEInfoRef = MutableListenableRef(OSEInfo())

    /**
     * Represents the current OSE Info and this should be used by consumers and listen to the value
     * changes
     */
    val oseInfo = mutableOSEInfoRef.asListenable()

    @Inject
    constructor(
        @ApplicationContext context: Context,
        tracker: DaggerSingletonTracker,
    ) : this(
        context,
        SecureStringObserver(context, Handler(OSE_LOOPER), SEARCH_ENGINE_SETTINGS_KEY),
    ) {
        settingsObserver.callback = Runnable { reloadOse() }
        handler.post { reloadOse() }
        tracker.addCloseable(this::close)
    }

    @WorkerThread
    @VisibleForTesting
    fun reloadOse() {
        Preconditions.assertNonUiThread()
        val osePkg: String? = settingsObserver.getValue()
        val overlayAppsList =
            context.resources.getStringArray(R.array.supported_overlay_apps).asList()
        // Look into the "supported_overlay_apps" Array based on OsePackage and fallback to first
        // entry in overlay or null
        val overlayPkg: String? =
            if (overlayAppsList.contains(osePkg)) osePkg
            else if (!overlayAppsList.isEmpty()) overlayAppsList.get(0) else null

        val overlayTarget =
            if (overlayPkg == null) null
            else
                try {
                    context.packageManager
                        .resolveActivity(Intent(OVERLAY_ACTION).setPackage(overlayPkg), 0)
                        ?.activityInfo
                } catch (e: Exception) {
                    null
                }

        val oldOseInfo = mutableOSEInfoRef.value
        val newOseInfo = OSEInfo(osePkg, overlayTarget)

        if (
            oldOseInfo.pkg != newOseInfo.pkg ||
                oldOseInfo.overlayPackage != newOseInfo.overlayPackage
        ) {
            packageAvailableReceiver.unregisterReceiverSafely()
            // Listen for ose changes
            if (osePkg != null) {
                packageAvailableReceiver.registerPkgActions(
                    osePkg,
                    ACTION_PACKAGE_ADDED,
                    ACTION_PACKAGE_CHANGED,
                    ACTION_PACKAGE_REMOVED,
                )
            }

            // Listen for overlay changes
            if (overlayPkg != null && osePkg != overlayPkg) {
                packageAvailableReceiver.registerPkgActions(
                    overlayPkg,
                    ACTION_PACKAGE_ADDED,
                    ACTION_PACKAGE_CHANGED,
                    ACTION_PACKAGE_REMOVED,
                )
            }

            mutableOSEInfoRef.dispatchValue(newOseInfo)
        }
    }

    @VisibleForTesting
    fun close() {
        settingsObserver.close()
        packageAvailableReceiver.unregisterReceiverSafely()
    }

    /** Object representing properties of the on-device search engine */
    class OSEInfo(val pkg: String? = null, val overlayTarget: ActivityInfo? = null) {
        val overlayPackage: String?
            get() = overlayTarget?.packageName ?: pkg
    }

    companion object {

        const val SEARCH_ENGINE_SETTINGS_KEY = "selected_search_engine"

        private val OSE_LOOPER = LooperExecutor.createAndStartNewLooper("OSEManager")

        private const val TAG = "OSEManager"

        const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
    }
}
