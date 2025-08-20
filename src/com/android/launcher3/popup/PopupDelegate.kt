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

package com.android.launcher3.popup

import android.graphics.Rect
import android.view.MotionEvent

/** Delegate interface for popup views. It handles common functionality between popups. */
interface PopupDelegate {

    /**
     * Determines what to do when there's a touch event
     *
     * @return true if we should intercept touch
     */
    fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean

    /**
     * Provide the location of the target object relative to the dragLayer.
     *
     * @param outPos is the location before adjusting for padding/margins.
     */
    fun getTargetObjectLocation(outPos: Rect)

    /**
     * Checks if the type of view we're looking at is TYPE_ACTION_POPUP
     *
     * @param type is the type of AbstractFloatingView
     * @return true if the type is popup
     */
    fun isOfType(type: Int): Boolean

    /** Handles actions for the popup when we start dragging. */
    fun onDragStart()

    /** Handles actions for the popup when we stop dragging. */
    fun onDragEnd()

    /**
     * Checks if we should start dragging.
     *
     * @param distanceDragged is the distance that we have dragged the item so far.
     * @return true if we should start drag.
     */
    fun shouldStartDrag(distanceDragged: Double): Boolean
}
