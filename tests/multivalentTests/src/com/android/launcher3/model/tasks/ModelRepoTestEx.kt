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

package com.android.launcher3.model.tasks

import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceChangeEvent.RemoveEvent
import com.android.launcher3.model.data.WorkspaceChangeEvent.UpdateEvent
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.util.Executors
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat

object ModelRepoTestEx {

    fun <T> ListenableRef<T>.trackUpdate() =
        mutableListOf<T>().also { updates ->
            TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {}
            forEach(Executors.MODEL_EXECUTOR) { updates.add(it) }
        }

    /** Verifies that the update list contains an update operation, and returns the updated items */
    fun List<WorkspaceData>.verifyAndGetItemsUpdated(
        updateIndex: Int = 1,
        totalUpdates: Int = 2,
    ): List<ItemInfo> {
        assertThat(this).hasSize(totalUpdates)
        val initialData = this[updateIndex - 1]
        val finalData = this[updateIndex]
        val diff = finalData.diff(initialData)!!
        assertThat(diff).hasSize(1)
        val updates = diff[0] as UpdateEvent
        return updates.items
    }

    /** Verifies that the update list contains a delete operation */
    fun List<WorkspaceData>.verifyDelete(deleteIndex: Int = 1, totalUpdates: Int = 2) {
        assertThat(this).hasSize(totalUpdates)
        val initialData = this[deleteIndex - 1]
        val finalData = this[deleteIndex]
        val diff = finalData.diff(initialData)!!
        assertThat(diff).hasSize(1)
        assertThat(diff[0]).isInstanceOf(RemoveEvent::class.java)
    }
}
