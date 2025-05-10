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

import android.util.SparseArray
import androidx.core.util.putAll
import androidx.core.util.valueIterator
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.Workspace
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet
import java.util.Collections
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * An immutable representation of all the workspace items (shortcuts, folders, widgets and predicted
 * items)
 */
sealed interface WorkspaceData : Iterable<ItemInfo> {

    /** Creates an array of valid workspace screens based on current items in the model. */
    fun collectWorkspaceScreens(): IntArray {
        val screenSet = IntSet()
        forEach { if (it.container == CONTAINER_DESKTOP) screenSet.add(it.screenId) }
        if (BuildConfig.QSB_ON_FIRST_SCREEN || screenSet.isEmpty) {
            screenSet.add(Workspace.FIRST_SCREEN_ID)
        }
        return screenSet.array
    }

    /** Returns the [ItemInfo] associated with the [id] or null */
    operator fun get(id: Int): ItemInfo?

    fun stream(): Stream<ItemInfo> = StreamSupport.stream(spliterator(), false)

    /** Version determines the uniqueness per model load cycle */
    val version: Int

    /**
     * Returns the predicted items for the provided [containerId] or an empty list id no such
     * container exists
     */
    fun getPredictedContents(containerId: Int): List<ItemInfo> =
        get(containerId).let { if (it is PredictedContainerInfo) it.getContents() else null }
            ?: Collections.emptyList()

    /** Returns an immutable copy of the dataset */
    fun copy(): WorkspaceData

    /** A mutable implementation of [WorkspaceData] */
    class MutableWorkspaceData : WorkspaceData {

        private val itemsIdMap = SparseArray<ItemInfo>()

        override var version: Int = 0

        private var modificationId: Int = 0

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        /** Replaces the existing dataset with [items] */
        fun replaceDataMap(items: SparseArray<ItemInfo>) {
            itemsIdMap.clear()
            itemsIdMap.putAll(items)
            version++
            modificationId = 0
        }

        /** Adds the [item] to the dataset */
        fun addItem(item: ItemInfo, owner: Any?) {
            itemsIdMap[item.id] = item
            modificationId++
        }

        /** Removes existing [items] from the dataset */
        fun removeItems(items: Collection<ItemInfo>, owner: Any?) {
            items.forEach { itemsIdMap.remove(it.id) }
            modificationId++
        }

        /** Replaces an existing [item] from the dataset */
        fun replaceItem(item: ItemInfo, owner: Any?) {
            itemsIdMap[item.id] = item
            notifyItemsUpdated(listOf(item), owner)
        }

        /**
         * Notifies this dataset that or updates already performed on existing [items]. Since the
         * underlying [ItemInfo]s are mutable objects, its possible to update their properties
         * without going though this dataset
         */
        fun notifyItemsUpdated(items: List<ItemInfo>, owner: Any?) {
            modificationId++
        }

        override fun copy(): WorkspaceData =
            ImmutableWorkspaceData(version, modificationId, itemsIdMap)
    }

    /** An immutable implementation of [WorkspaceData] */
    class ImmutableWorkspaceData(
        override val version: Int,
        private val modificationId: Int,
        items: SparseArray<ItemInfo>,
    ) : WorkspaceData {

        private val itemsIdMap = items.clone()

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        override fun copy(): WorkspaceData = this
    }
}
