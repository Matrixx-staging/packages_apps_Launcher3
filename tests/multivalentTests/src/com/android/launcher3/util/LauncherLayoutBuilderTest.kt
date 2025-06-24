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

package com.android.launcher3.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY3
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherLayoutBuilderTest {

    @Test
    fun layoutBuilderFolderInFolderFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putFolder("OuterFolder")
                .putFolder("InnerFolder")
        }
    }

    @Test
    fun layoutBuilderAppPairInAppPairFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("OuterAppPair")
                .putAppPair("InnerAppPair")
        }
    }

    @Test
    fun layoutBuilderMoreThan2ItemAppPairFails() {
        assertThrows(IllegalStateException::class.java) {
            LauncherLayoutBuilder()
                .atWorkspace(1, 1, 1)
                .putAppPair("OuterAppPair")
                .addApp(TEST_PACKAGE, TEST_ACTIVITY)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY2)
                .addApp(TEST_PACKAGE, TEST_ACTIVITY3)
        }
    }
}
