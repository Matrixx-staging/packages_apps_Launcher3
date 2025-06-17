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

package com.android.launcher3.popup

import android.content.Context
import android.util.SparseArray
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.R
import com.android.launcher3.Workspace.mapOverCellLayouts
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.SystemShortcut.Factory
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

        for (i in 0 until dp.numShownHotseatIcons) {
            if (mPinnedInfoList[i] == null) {
                targetIdx = i
                break
            }
        }

        val cellX = if (dp.isVerticalBarLayout()) 0 else targetIdx
        val cellY = if (dp.isVerticalBarLayout()) (dp.numShownHotseatIcons - (targetIdx + 1)) else 0

        writer.addItemToDatabase(newInfo, CONTAINER_HOTSEAT, mItemInfo.screenId, cellX, cellY)
        mOnClickCallback?.run()
    }

    companion object {
        @JvmField
        val PIN_ITEM_FROM_LAUNCHER: Factory<Launcher> = Factory { context, itemInfo, originalView ->
            if (context !is Launcher) {
                return@Factory null
            }

            val hotseat = context.hotseat
            val hotseatInfosList = SparseArray<ItemInfo?>()

            val isPinnedInHotseat =
                mapOverCellLayouts(arrayOf(hotseat)) { info, _ ->
                    info?.componentKey == itemInfo?.componentKey
                } != null

            mapOverCellLayouts(arrayOf(hotseat)) { info, _ ->
                if (info != null && !info.isPredictedItem) {
                    // In hotseat, the screenId is often used as the rank or position.
                    hotseatInfosList.put(info.screenId, info)
                }
                false // Return false to continue iterating through all items
            }

            if (isPinnedInHotseat) {
                // As the item is already pinned, return a shortcut to UNPIN it.
                return@Factory PinToTaskbarShortcut<Launcher>(
                    context,
                    itemInfo,
                    originalView,
                    false,
                    hotseatInfosList,
                )
            }

            if (hotseatInfosList.size() < context.deviceProfile.numShownHotseatIcons) {
                return@Factory PinToTaskbarShortcut<Launcher>(
                    context,
                    itemInfo,
                    originalView,
                    true,
                    hotseatInfosList,
                    context::onItemPinnedFromContextMenu,
                )
            }

            return@Factory null
        }
    }
}
