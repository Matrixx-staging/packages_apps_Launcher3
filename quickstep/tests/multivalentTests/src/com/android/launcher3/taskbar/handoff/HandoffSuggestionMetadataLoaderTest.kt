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

package com.android.launcher3.taskbar.handoff

import android.companion.datatransfer.continuity.RemoteTask
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.TestLooperManager
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
class HandoffSuggestionMetadataLoaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
    private val icon = Icon.createWithBitmap(bitmap)

    private val handler = Handler(context.mainLooper)
    private val suggestionMetadataLoader = HandoffSuggestionMetadataLoader(context, handler)

    private lateinit var testLooperManager: TestLooperManager

    @Before
    fun setUp() {
        testLooperManager =
            InstrumentationRegistry.getInstrumentation().acquireLooperManager(context.mainLooper)
    }

    @After
    fun tearDown() {
        testLooperManager.release()
    }

    @Test
    fun loadMetadata_drawableLoaded_callsCallback() {
        // Create a fake suggestion to load
        val remoteTask = RemoteTask.Builder(1).setIcon(icon).setLabel(TEST_LABEL).build()
        val suggestion = HandoffSuggestion(remoteTask)

        // Trigger a load.
        val loadedSuggestions = mutableListOf<HandoffSuggestion>()
        suggestionMetadataLoader.loadMetadata(listOf(suggestion)) { loadedSuggestions.add(it) }
        val message = testLooperManager.next()
        testLooperManager.execute(message)
        testLooperManager.recycle(message)

        // Verify the callback was called with the correct metadata.
        assertThat(loadedSuggestions).hasSize(1)
        assertThat(loadedSuggestions.first().metadata?.label).isEqualTo(TEST_LABEL)
        val drawable = loadedSuggestions.first().metadata?.icon
        assertThat(drawable).isInstanceOf(BitmapDrawable::class.java)
        val bitmapDrawable = drawable as BitmapDrawable
        assertThat(bitmapDrawable.bitmap.sameAs(bitmap)).isTrue()
    }

    @Test
    fun loadMetadata_allSuggestionsHaveMetadata_doesNotReloadIcons() {
        // Create a fake suggestion with loaded metadata
        val remoteTask = RemoteTask.Builder(1).setIcon(icon).setLabel(TEST_LABEL).build()
        val suggestion = HandoffSuggestion(remoteTask)
        suggestion.metadata =
            HandoffSuggestion.Metadata(TEST_LABEL, BitmapDrawable(context.resources, bitmap))

        // Trigger a load.
        var wasCallbackCalled = false
        suggestionMetadataLoader.loadMetadata(listOf(suggestion)) { wasCallbackCalled = true }

        // Verify no loads were queued.
        assertThat(testLooperManager.poll()).isNull()
        assertThat(wasCallbackCalled).isFalse()
    }

    @Test
    fun loadMetadata_taskHasNullIcon_doesNotCallCallback() {
        // Create a fake suggestion to load with a null icon
        val remoteTask = RemoteTask.Builder(1).setLabel(TEST_LABEL).build()
        val suggestion = HandoffSuggestion(remoteTask)

        // Trigger a load.
        var wasCallbackCalled = false
        suggestionMetadataLoader.loadMetadata(listOf(suggestion)) { wasCallbackCalled = true }

        // Verify no loads were queued.
        assertThat(testLooperManager.poll()).isNull()
        assertThat(wasCallbackCalled).isFalse()
    }

    @Test
    fun cancelPendingLoads_cancelsPendingLoads() {
        // Create a fake suggestion to load with a null icon
        val remoteTask = RemoteTask.Builder(1).setLabel(TEST_LABEL).build()
        val suggestion = HandoffSuggestion(remoteTask)

        // Trigger a load.
        var wasCallbackCalled = false
        suggestionMetadataLoader.loadMetadata(listOf(suggestion)) { wasCallbackCalled = true }

        // Cancel the load.
        suggestionMetadataLoader.cancelPendingLoads()

        // Verify no loads were queued.
        assertThat(testLooperManager.poll()).isNull()
        assertThat(wasCallbackCalled).isFalse()
    }

    companion object {
        private val TEST_LABEL = "test_label"
    }
}
