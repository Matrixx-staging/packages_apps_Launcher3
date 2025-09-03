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

package com.android.launcher3.homescreenfiles

import android.net.Uri
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController

/** Handles changes in file items shown on the home screen. */
class HomeScreenFilesChangedTask(private val uri: Uri, private val flags: Int) :
    LauncherModel.ModelUpdateTask {
    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        // TODO(b/424467033): add proper implementation, handle insert/update/delete events and
        // update/bind the corresponding item.
        taskController.deleteAndBindComponentsRemoved(
            {
                it?.itemType == Favorites.ITEM_TYPE_FILE_SYSTEM_FILE ||
                    it?.itemType == Favorites.ITEM_TYPE_FILE_SYSTEM_FOLDER
            },
            "wip (b/424467033)",
        )
        taskController.model.forceReload()
    }
}
