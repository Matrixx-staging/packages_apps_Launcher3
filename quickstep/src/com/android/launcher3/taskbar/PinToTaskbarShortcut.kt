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

package com.android.launcher3.taskbar

import android.content.Context
import android.util.SparseArray
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.launcher3.DeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelWriter
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.SystemShortcut.Factory
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.views.ActivityContext

/**
 * A single menu item shortcut to allow users to pin an item to the taskbar and unpin an item from
 * the taskbar.
 */
class PinToTaskbarShortcut<T>
@JvmOverloads
constructor(
    target: T,
    itemInfo: ItemInfo?,
    originalView: View,
    @get:VisibleForTesting val mIsPin: Boolean,
    private val mPinnedInfoList: SparseArray<ItemInfo?>,
    private val mOnClickCallback: Runnable? = null,
) :
    SystemShortcut<T>(
        if (mIsPin) R.drawable.ic_pin else R.drawable.ic_unpin,
        if (mIsPin) R.string.pin_to_taskbar else R.string.unpin_from_taskbar,
        target,
        itemInfo,
        originalView,
    ) where T : Context?, T : ActivityContext? {

    override fun onClick(v: View?) {
        dismissTaskMenuView()
        // Create a placeholder callbacks for the writer to notify other launcher model callbacks
        // after update.
        val callbacks: BgDataModel.Callbacks = object : BgDataModel.Callbacks {}

        val writer =
            LauncherAppState.getInstance(mOriginalView.context)
                .model
                .getWriter(true, mTarget!!.cellPosMapper, callbacks)

        if (!mIsPin) {
            var infoToUnpin = mItemInfo
            if (mItemInfo.isInAllApps) {
                for (i in 0..<mPinnedInfoList.size()) {
                    if (
                        mPinnedInfoList.valueAt(i)?.getComponentKey() == mItemInfo.getComponentKey()
                    ) {
                        infoToUnpin = mPinnedInfoList.valueAt(i)
                        break
                    }
                }
            }
            writer.deleteItemFromDatabase(infoToUnpin, "item unpinned through long-press menu")
            mOnClickCallback?.run()
            return
        }

        val newInfo =
            if (mItemInfo is com.android.launcher3.model.data.AppInfo) {
                mItemInfo.makeWorkspaceItem(mOriginalView.context)
            } else if (mItemInfo is WorkspaceItemInfo) {
                mItemInfo.clone()
            } else {
                return
            }

        val dp: DeviceProfile = mTarget.deviceProfile
        var targetIdx = -1

        // Reorder the taskbar only if we can't find a space that is to the right of all other
        // items.
        if (mPinnedInfoList[dp.numShownHotseatIcons - 1] != null) {
            compactTaskbarItems(writer)
        }

        // Find the first available space that has larger index than all other items.
        for (i in dp.numShownHotseatIcons - 1 downTo 0) {
            if (mPinnedInfoList[i] == null) {
                targetIdx = i
            } else {
                break
            }
        }

        val (cellX, cellY) = getCellCoordinates(targetIdx)

        writer.addItemToDatabase(newInfo, CONTAINER_HOTSEAT, mItemInfo.screenId, cellX, cellY)
        mOnClickCallback?.run()
    }

    /**
     * Moves all the taskbar items to the front so that spaces that don't have a pinned item will be
     * at the end of the taskbar. This can ensure that the newly pinned app will be appended to the
     * end of the taskbar.
     */
    private fun compactTaskbarItems(writer: ModelWriter) {
        if (mIsPin && mPinnedInfoList.size() > 0) {
            val dp: DeviceProfile = mTarget!!.deviceProfile
            val nonNullItems = mutableListOf<ItemInfo>()
            // Collect existing non-null items in their current order (based on SparseArray keys)
            for (i in 0 until dp.numShownHotseatIcons) {
                mPinnedInfoList.get(i)?.let { nonNullItems.add(it) }
            }
            // Update database for moved items
            for (newScreenId in nonNullItems.indices) {
                val itemToUpdate = nonNullItems[newScreenId]

                // Calculate new cellX, cellY based on newScreenId
                val (newCellX, newCellY) = getCellCoordinates(newScreenId)

                if (
                    itemToUpdate.screenId != newScreenId ||
                        itemToUpdate.cellX != newCellX ||
                        itemToUpdate.cellY != newCellY
                ) {
                    itemToUpdate.screenId = newScreenId
                    itemToUpdate.cellX = newCellX
                    itemToUpdate.cellY = newCellY
                    // container remains CONTAINER_HOTSEAT
                    writer.updateItemInDatabase(itemToUpdate)
                }
            }

            // Update the mPinnedInfoList in memory to reflect the new state
            mPinnedInfoList.clear()
            for (i in nonNullItems.indices) {
                mPinnedInfoList.put(i, nonNullItems[i])
            }
            for (i in nonNullItems.size until dp.numShownHotseatIcons) {
                mPinnedInfoList.put(i, null)
            }
        }
    }

    /** This should be the same as how Hotseat calculates cellX and cellY from a rank. */
    private fun getCellCoordinates(targetIdx: Int): Pair<Int, Int> {
        val dp: DeviceProfile = mTarget!!.deviceProfile
        val cellX = if (dp.isVerticalBarLayout) 0 else targetIdx
        val cellY = if (dp.isVerticalBarLayout) (dp.numShownHotseatIcons - (targetIdx + 1)) else 0

        return Pair(cellX, cellY)
    }

    companion object {
        @JvmField
        val PIN_ITEM_FROM_LAUNCHER: Factory<QuickstepLauncher> =
            Factory { context, itemInfo, originalView ->
                val taskbarInfoList =
                    context.taskbarUIController
                        ?.mControllers
                        ?.taskbarPopupController
                        ?.taskbarInfoList ?: return@Factory null

                var isPinnedInTaskbar = false
                for (i in 0 until taskbarInfoList.size()) {
                    if (taskbarInfoList.valueAt(i)?.componentKey == itemInfo?.componentKey) {
                        isPinnedInTaskbar = true
                        break
                    }
                }

                if (isPinnedInTaskbar) {
                    // As the item is already pinned, return a shortcut to UNPIN it.
                    return@Factory PinToTaskbarShortcut<QuickstepLauncher>(
                        context,
                        itemInfo,
                        originalView,
                        false,
                        taskbarInfoList,
                    )
                }

                if (taskbarInfoList.size() < context.deviceProfile.numShownHotseatIcons) {
                    return@Factory PinToTaskbarShortcut<QuickstepLauncher>(
                        context,
                        itemInfo,
                        originalView,
                        true,
                        taskbarInfoList,
                        context::onItemPinnedFromContextMenu,
                    )
                }

                return@Factory null
            }
    }
}
