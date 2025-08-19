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

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.views.ActivityContext

class PopupDelegateImpl<T>(val originalView: View, private val popup: ArrowPopup<T>) :
    PopupDelegate where T : Context, T : ActivityContext {

    private val startDragThreshold =
        originalView.context.resources.getDimensionPixelSize(
            R.dimen.deep_shortcuts_start_drag_threshold
        )

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dl = popup.popupContainer
            if (!dl.isEventOverView(popup, ev)) {
                // TODO: add WW log if want to log if tap closed deep shortcut container.
                popup.close(true)

                // We let touches on the original view go through so that users can launch
                // the item with one tap.
                return !dl.isEventOverView(originalView, ev)
            }
        }
        return false
    }

    override fun getTargetObjectLocation(outPos: Rect) {
        popup.popupContainer.getDescendantRectRelativeToSelf(originalView, outPos)
        outPos.top += originalView.paddingTop
        outPos.left += originalView.paddingLeft
        outPos.right -= originalView.paddingRight
        outPos.bottom =
            outPos.top +
                if (originalView is BubbleTextView && originalView.icon != null)
                    originalView.icon.bounds.height()
                else (originalView.height)
    }

    override fun isOfType(type: Int): Boolean {
        return (type and AbstractFloatingView.TYPE_ACTION_POPUP) != 0
    }

    override fun onDragStart() {
        // Either the original item or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        popup.mDeferContainerRemoval = true
        popup.animateClose()
    }

    override fun onDragEnd() {
        if (!popup.isOpen) {
            if (popup.mOpenCloseAnimator != null) {
                // Close animation is running.
                popup.mDeferContainerRemoval = false
            } else {
                // Close animation is not running.
                if (popup.mDeferContainerRemoval) {
                    popup.closeComplete()
                }
            }
        }
    }

    override fun shouldStartDrag(distanceDragged: Double): Boolean {
        return distanceDragged > startDragThreshold
    }
}
