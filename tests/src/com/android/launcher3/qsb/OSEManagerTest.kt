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

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.qsb.OSEManager.Companion.OVERLAY_ACTION
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SecureStringObserver
import com.android.launcher3.util.TestUtil
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

/** Unit tests for OSEManager. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class OSEManagerTest {

    val context = spy(SandboxApplication())
    private val settingsObserver: SecureStringObserver = mock()
    private val oseManager: OSEManager by lazy {
        OSEManager(context, settingsObserver, UI_HELPER_EXECUTOR.looper)
    }
    val res = spy(context.resources)
    lateinit var listenableRefClosable: SafeCloseable
    val mockCallback: (OSEManager.OSEInfo) -> Unit = mock()

    @After
    fun tearDown() {
        oseManager.close()
        UI_HELPER_EXECUTOR.submit {}.get()
        listenableRefClosable.close()
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        doReturn(res).whenever(context).resources
        doReturn(emptyArray<String>())
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))
        listenableRefClosable = oseManager.oseInfo.forEach(UI_HELPER_EXECUTOR, mockCallback)
    }

    @Test
    fun testOsePkgIsNull() {
        doReturn(null).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertNull(oseManager.oseInfo.value.pkg)
    }

    @Test
    fun `overlay null when supported_overlay_apps is empty`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertEquals(BING_PKG, oseManager.oseInfo.value.pkg)
        assertNull(oseManager.oseInfo.value.overlayTarget)
    }

    @Test
    fun `overlay fallback to first entry in supported_overlay_apps for unsupported overlay`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()
        context.packageManager.mockOverlayResolution(DUCK_PKG, mockResolverInfo(DUCK_PKG))
        doReturn(arrayOf(DUCK_PKG, GOOGLE_PACKAGE))
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertEquals(BING_PKG, oseManager.oseInfo.value.pkg)
        assertNotNull(oseManager.oseInfo.value.overlayTarget)
        assertEquals(DUCK_PKG, oseManager.oseInfo.value.overlayTarget?.packageName)
    }

    @Test
    fun `overlay matches the valid supported overlay`() {
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        context.packageManager.mockOverlayResolution(DUCK_PKG, mockResolverInfo(DUCK_PKG))
        doReturn(arrayOf(GOOGLE_PACKAGE, DUCK_PKG, BING_PKG))
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertEquals(DUCK_PKG, oseManager.oseInfo.value.pkg)
        assertNotNull(oseManager.oseInfo.value.overlayTarget)
        assertEquals(DUCK_PKG, oseManager.oseInfo.value.overlayTarget?.packageName)
    }

    @Test
    fun `callback invoked when OseInfo changes`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        assertEquals(BING_PKG, oseManager.oseInfo.value.pkg)
        Mockito.verify(mockCallback, times(1)).invoke(any())

        // OSE Package unchanged.
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        Mockito.verify(mockCallback, times(1)).invoke(any())

        // Change the OSE package
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        Mockito.verify(mockCallback, times(2)).invoke(any())
        assertEquals(DUCK_PKG, oseManager.oseInfo.value.pkg)
    }

    private fun mockResolverInfo(pkg: String) =
        ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = pkg
                    name = "test"
                }
        }

    private fun PackageManager.mockOverlayResolution(pkg: String, info: ResolveInfo?) {
        doReturn(info)
            .whenever(this)
            .resolveActivity(
                argThat<Intent> { OVERLAY_ACTION == action && pkg == getPackage() },
                eq(0),
            )
    }

    companion object {
        private const val DUCK_PKG = "com.duckduckgo"
        private const val BING_PKG = "com.bing"
        private const val GOOGLE_PACKAGE = "com.google"
    }
}
