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

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.RoundedCornerEnforcement

/**
 * Renders the On-device search engine's widget [RemoteViews] based on [AppWidgetProviderInfo] by
 * listening to OSE changes through [OseWidgetManager]
 */
class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppWidgetHostView(context), View.OnLongClickListener {

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    private val enforcedCornerRadius: Float
    private val enforcedRectangle = Rect()
    @VisibleForTesting val closeActions = RunnableList()
    val mLongPressHelper = CheckLongPressHelper(this)
    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    init {
        enforcedCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context)
        clipToOutline = true
        this.setOnLongClickListener(this)
    }

    private val cornerRadiusEnforcementOutline =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                if (enforcedRectangle.isEmpty() || enforcedCornerRadius <= 0) {
                    outline?.setEmpty()
                } else {
                    outline?.setRoundRect(enforcedRectangle, enforcedCornerRadius)
                }
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow()
    }

    @VisibleForTesting
    fun attachedToWindow() {
        closeActions.executeAllAndClear()
        // We use INVALID_APPWIDGET_ID because appWidgetId is not tracked in OseWidgetView. Instead
        // it is managed by OseWidgetManager and QsbAppWidgetHost.
        closeActions.add(
            oseWidgetManager.providerInfo.forEach(MAIN_EXECUTOR) {
                setAppWidget(INVALID_APPWIDGET_ID, it)
            }::close
        )
        closeActions.add(
            oseWidgetManager.views.forEach(MAIN_EXECUTOR, { updateAppWidget(it) })::close
        )
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // Prevent default padding being set on the view based on provider info. Launcher manages
        // its own widget spacing.
        // Do Nothing
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachedFromWindow()
    }

    @VisibleForTesting
    fun detachedFromWindow() {
        closeActions.executeAllAndClear()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        enforceRoundedCorners()
    }

    private fun enforceRoundedCorners() {
        if (enforcedCornerRadius <= 0) {
            if (DEBUG) {
                Log.i(TAG, " enforcedCornerRadius is <=0 " + enforcedCornerRadius)
            }
            outlineProvider = VIEW_OUTLINE_PROVIDER
            return
        }
        val background = RoundedCornerEnforcement.findBackground(this)
        if (background == null || RoundedCornerEnforcement.hasAppWidgetOptedOut(background)) {
            if (DEBUG) {
                Log.i(TAG, " background " + background)
            }
            outlineProvider = VIEW_OUTLINE_PROVIDER
            return
        }
        RoundedCornerEnforcement.computeRoundedRectangle(this, background, enforcedRectangle)
        if (DEBUG) {
            Log.i(TAG, " enforcedRectangle " + enforcedRectangle)
        }
        outlineProvider = cornerRadiusEnforcementOutline
        invalidateOutline()
    }

    override fun shouldDelayChildPressedState(): Boolean {
        // Delay the ripple effect on the widget view when swiping up from home screen
        // to go to all apps.
        return true
    }

    override fun getErrorView(): View =
        View.inflate(context, R.layout.ose_default_layout, null).apply {
            setOnClickListener {
                context.startActivity(
                    Intent(Intent.ACTION_SEARCH)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                )
            }
        }

    override fun onLongClick(view: View?): Boolean {
        val oseWidgetOptionsProvider =
            activityContext.activityComponent.getOseWidgetOptionsProvider()
        val optionItems = oseWidgetOptionsProvider.getOptionItems()
        if (optionItems.isEmpty()) return false

        val bounds =
            RectF(Utilities.getViewBounds(this)).apply {
                left = centerX()
                right = centerX()
            }
        showOptionsPopup(bounds, optionItems)
        return true
    }

    @VisibleForTesting
    fun showOptionsPopup(bounds: RectF, optionItems: List<OptionItem>) {
        OptionsPopupView.showNoReturn(activityContext, bounds, optionItems, true)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        mLongPressHelper.onTouchEvent(ev)
        return mLongPressHelper.hasPerformedLongPress()
    }

    companion object {
        private const val TAG = "OseWidgetView"
        private const val DEBUG = false
        private val VIEW_OUTLINE_PROVIDER: ViewOutlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    // We should restrict the outline to be the view bounds, otherwise widgets might
                    // draw themselves outside of the launcher view.
                    // Setting alpha to 0 to match the previous behavior.
                    outline.setRect(0, 0, view.width, view.height)
                    outline.alpha = .0f
                }
            }
    }
}
