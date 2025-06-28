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
import android.util.AttributeSet
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList

/**
 * Renders the On-device search engine's widget [RemoteViews] based on [AppWidgetProviderInfo] by
 * listening to OSE changes through [OseWidgetManager]
 */
class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppWidgetHostView(context) {

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    @VisibleForTesting val closeActions = RunnableList()

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
            oseWidgetManager.providerInfo.forEach(
                MAIN_EXECUTOR,
                { setAppWidget(INVALID_APPWIDGET_ID, it) },
            )::close
        )
        setAppWidget(INVALID_APPWIDGET_ID, oseWidgetManager.providerInfo.value)

        closeActions.add(
            oseWidgetManager.views.forEach(MAIN_EXECUTOR, { updateAppWidget(it) })::close
        )
        updateAppWidget(oseWidgetManager.views.value)
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
}
