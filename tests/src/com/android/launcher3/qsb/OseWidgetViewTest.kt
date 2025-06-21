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

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.ui.TestViewHelpers
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OseWidgetViewTest {
    private lateinit var mVut: OseWidgetView
    private lateinit var oseWidgetManager: OseWidgetManager
    private val context = ActivityContextWrapper(getApplicationContext())
    private val widgetInfo = TestViewHelpers.findWidgetProvider(false)
    private val remoteView = RemoteViews(widgetInfo.provider.packageName, 0)
    private val mockProviderInfo = MutableListenableRef<AppWidgetProviderInfo>(widgetInfo)
    private val mockRemoteViews = MutableListenableRef(remoteView)

    @Before
    fun setUp() {
        mVut = OseWidgetView(context)
        spyOn(mVut)
        spyOn(mVut.closeActions)
        doNothing().whenever(mVut).setAppWidget(any(), any())
        oseWidgetManager = context.appComponent.oseWidgetManager
        spyOn(oseWidgetManager)
        doReturn(mockProviderInfo).whenever(oseWidgetManager).providerInfo
        doReturn(mockRemoteViews).whenever(oseWidgetManager).views
    }

    @Test
    fun when_view_attachedToWindow() {
        mVut.attachedToWindow()
        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        verify(mVut).updateAppWidget(remoteView)
        verify(mVut.closeActions).executeAllAndClear()
        verify(mVut.closeActions, times(2)).add(any())
    }

    @Test
    fun when_providerInfo_changes() {
        mVut.attachedToWindow()
        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(newWidgetInfo)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(mVut).setAppWidget(INVALID_APPWIDGET_ID, newWidgetInfo)
        verify(mVut, times(1)).updateAppWidget(remoteView)
    }

    @Test
    fun when_remoteView_changes() {
        mVut.attachedToWindow()
        verify(mVut).updateAppWidget(remoteView)

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val newRemoteView = RemoteViews(newWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(newRemoteView)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        verify(mVut, times(1)).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        verify(mVut).updateAppWidget(newRemoteView)
    }

    @Test
    fun when_providerInfo_changes_after_view_detachedFromWindow() {
        mVut.attachedToWindow()
        verify(mVut, times(1)).setAppWidget(INVALID_APPWIDGET_ID, widgetInfo)
        mVut.detachedFromWindow()
        verify(mVut.closeActions, times(2)).executeAllAndClear()

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(newWidgetInfo)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        // setAppWidget is not called since view is detached even though providerInfo changes
        verify(mVut, times(1)).setAppWidget(any(), any())

        val anotherWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        mockProviderInfo.dispatchValue(anotherWidgetInfo)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        // setAppWidget is not called since view is detached even though providerInfo changes
        verify(mVut, times(1)).setAppWidget(any(), any())
    }

    @Test
    fun when_remoteView_changes_after_view_detachedFromWindow() {
        mVut.attachedToWindow()
        verify(mVut, times(1)).updateAppWidget(remoteView)
        mVut.detachedFromWindow()
        verify(mVut.closeActions, times(2)).executeAllAndClear()

        val newWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val newRemoteView = RemoteViews(newWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(newRemoteView)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        // updateAppWidget is not called since view is detached even though remoteView changes
        verify(mVut, times(1)).updateAppWidget(any())

        val anotherWidgetInfo = TestViewHelpers.findWidgetProvider(false)
        val anotherRemoteView = RemoteViews(anotherWidgetInfo.provider.packageName, 0)
        mockRemoteViews.dispatchValue(anotherRemoteView)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        // updateAppWidget is not called since view is detached even though remoteView changes
        verify(mVut, times(1)).updateAppWidget(any())
    }
}
