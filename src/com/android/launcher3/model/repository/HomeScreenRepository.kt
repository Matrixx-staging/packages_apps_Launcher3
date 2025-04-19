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

package com.android.launcher3.model.repository

import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.repository.HomeScreenRepository.WorkspaceData.ChangeEvent
import com.android.launcher3.util.Executors
import com.android.launcher3.util.IntSparseArrayMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository for the home screen data.
 *
 * This class is responsible for holding the current state of the home screen and providing a way to
 * listen for changes to the data.
 */
@LauncherAppSingleton
class HomeScreenRepository @Inject constructor() {

    /**
     * Represents the current home screen data model. There are two ways this can change:
     * 1) The model can be replaced completely with a new data, which can be observed using the
     *    state flow.
     * 2) Changes can be made to the existing data, the diff can be observed using
     *    [WorkspaceData.updates]
     */
    val workspaceStateFlow = MutableStateFlow(WorkspaceData(IntSparseArrayMap()))

    class WorkspaceData(var itemsIdMap: IntSparseArrayMap<ItemInfo>) {

        val scope = MainScope()

        internal val updateListeners = CopyOnWriteArrayList<Consumer<ChangeEvent>>()

        val updates =
            callbackFlow<ChangeEvent> {
                val listener = Consumer<ChangeEvent> { trySend(it) }
                updateListeners.add(listener)
                awaitClose { updateListeners.remove(listener) }
            }

        /** Represents a change being made to the existing workspace data */
        sealed interface ChangeEvent {
            // The items being changed
            val items: List<ItemInfo>

            // The source of the change. If its user driven, it will point to the UI component where
            // the user is interacting or null if the change was made as a result of some system
            // event. Clients can use this to exclude self-made changes.
            val owner: Any?

            /** New items were added to the model */
            data class AddEvent(override val items: List<ItemInfo>, override val owner: Any?) :
                ChangeEvent

            /** Some properties of existing items changed */
            data class UpdateEvent(override val items: List<ItemInfo>, override val owner: Any?) :
                ChangeEvent

            /** Some items were removed from the model */
            data class RemoveEvent(override val items: List<ItemInfo>, override val owner: Any?) :
                ChangeEvent
        }
    }

    /**
     * Used to notify that the model data was completely replaced. This is only meant to be used by
     * the model, clients should just rely on the events provided by the StateFlow
     */
    fun onNewBind(model: BgDataModel) {
        val items = model.itemsIdMap.clone()

        Executors.MAIN_EXECUTOR.execute {
            workspaceStateFlow.value.scope.cancel()
            workspaceStateFlow.value = WorkspaceData(items)
        }
    }

    /**
     * Used to notify a particular change to the workspace data. This is only meant to be used by
     * the model, clients should just rely on the events provided by the StateFlow
     */
    fun dispatchChange(model: BgDataModel, event: ChangeEvent) {
        val items = model.itemsIdMap.clone()
        Executors.MAIN_EXECUTOR.execute {
            workspaceStateFlow.value.itemsIdMap = items
            workspaceStateFlow.value.updateListeners.forEach { it.accept(event) }
        }
    }
}
