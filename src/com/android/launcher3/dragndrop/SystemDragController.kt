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

package com.android.launcher3.dragndrop

import android.view.DragEvent
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Launcher
import com.android.launcher3.util.DaggerSingletonObject

/** Controller for system-level drag-and-drop. */
sealed class SystemDragController {

    /**
     * Returns whether a drop of the specified item info should be accepted.
     *
     * @param itemInfo The item info for which to determine acceptability.
     * @return Whether a drop should be accepted.
     */
    open fun acceptDrop(itemInfo: SystemDragItemInfo) = false

    /**
     * Sets the launcher for which to handle system-level drag-and-drop.
     *
     * @param launcher The launcher for which to handle system-level drag-and-drop.
     */
    open fun setLauncher(launcher: Launcher) {}

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject { launcherAppComponent ->
            INSTANCE_FOR_TESTING ?: launcherAppComponent.getSystemDragController()
        }

        /** NOTE: This allows for replacement of the singleton {@link INSTANCE} in testing. */
        @JvmField @VisibleForTesting var INSTANCE_FOR_TESTING: SystemDragController? = null
    }
}

/**
 * Stub implementation of the controller for system-level drag-and-drop. Injected when {@link
 * com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG} is disabled.
 */
class SystemDragControllerStub : SystemDragController()

/** Factory used to create listeners for system-level drag-and-drop. */
typealias SystemDragListenerFactory = (@JvmSuppressWildcards Launcher) -> SystemDragListener

/**
 * Production implementation of the controller for system-level drag-and-drop. Injected when {@link
 * com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG} is enabled.
 *
 * @param systemDragListenerFactory The factory used to create listeners for system-level
 *   drag-and-drop. A unique listener instance is created per handled drag-and-drop sequence.
 */
class SystemDragControllerImpl(private val systemDragListenerFactory: SystemDragListenerFactory) :
    SystemDragController(), DragController.SystemDragHandler {

    private var launcher: Launcher? = null
    private var systemDragListener: SystemDragListener? = null

    // TODO(b/440196898): Only accept URIs that can be mapped to Media Store URIs.
    override fun acceptDrop(itemInfo: SystemDragItemInfo) = itemInfo.uriList?.isEmpty() == false

    override fun onDrag(event: DragEvent): Boolean =
        continueDrag(event) ?: startDrag(event) ?: false

    override fun setLauncher(launcher: Launcher) {
        if (this.launcher != launcher) {
            this.launcher?.dragController?.removeSystemDragHandler(this)
            this.launcher = launcher.also { it.dragController?.addSystemDragHandler(this) }
        }
    }

    private fun continueDrag(event: DragEvent): Boolean? = systemDragListener?.onDrag(event)

    private fun startDrag(event: DragEvent): Boolean? =
        launcher?.run {
            dragController?.isDragging == false &&
                event.action == DragEvent.ACTION_DRAG_STARTED &&
                systemDragListenerFactory(this)
                    .also { listener ->
                        systemDragListener = listener
                        listener.setCleanupCallback {
                            if (systemDragListener == listener) {
                                systemDragListener = null
                            }
                        }
                    }
                    .onDrag(event)
        }
}
