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

package com.android.launcher3.model.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.ComponentKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResolvedTargetInfoTest {

    private val testTargetActivityComponent =
        ComponentName("com.example.target", "com.example.target.TargetActivity")
    private val testComponent = ComponentName("com.example.app", "com.example.app.MainActivity")
    private val testUser: UserHandle = UserHandle.getUserHandleForUid(0)

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNotNull returnsTargetActivityComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)
        val expectedKey = ComponentKey(testTargetActivityComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testTargetActivityComponent)
        assertThat(actualKey).isEqualTo(expectedKey)
        assertThat(actualKey.user).isEqualTo(testUser)
    }

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNull componentName isNotNull returnsComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)
        val expectedKey = ComponentKey(testComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testComponent)
        assertThat(actualKey.user).isEqualTo(testUser)
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    @Test
    fun `getTargetComponentKey both targetActivityComponentName and componentName areNull returnsNull`() {
        val resolvedInfo = ResolvedTargetInfo(null, null, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNull()
    }
}
