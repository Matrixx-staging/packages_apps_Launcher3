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

import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.model.data.ItemInfo
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import java.util.stream.Stream

class PopupDataRepositoryImpl(
    private val itemInfo: Array<out ItemInfo>,
    private val popupDataSource: PopupDataSource,
) : PopupDataRepository {
    private val popupData: MutableMap<PoppableType, Stream<PopupData>> = mutableMapOf()

    override fun getAllPopupData(): Map<PoppableType, Stream<PopupData>> {
        if (popupData.isEmpty()) {
            aggregatePopupData()
        }
        return popupData
    }

    override fun getPopupDataByType(type: PoppableType): Stream<PopupData>? {
        if (popupData.isEmpty()) {
            aggregatePopupData()
        }
        return popupData[type]
    }

    private fun aggregatePopupData() {
        itemInfo.forEach {
            if (it.itemType == ITEM_TYPE_FOLDER && PoppableType.FOLDER !in popupData) {
                popupData[PoppableType.FOLDER] = Stream.of(popupDataSource.removePopupData)
            }
            if (it.itemType == ITEM_TYPE_APP_PAIR && PoppableType.APP_PAIR !in popupData) {
                popupData[PoppableType.APP_PAIR] = Stream.of(popupDataSource.removePopupData)
            }
            if (it.itemType == ITEM_TYPE_APPWIDGET && PoppableType.WIDGET !in popupData) {
                popupData[PoppableType.WIDGET] =
                    Stream.of(
                        popupDataSource.removePopupData,
                        popupDataSource.widgetSettingsPopupData,
                    )
            }
            if (
                (it.itemType == ITEM_TYPE_APPLICATION || it.itemType == ITEM_TYPE_DEEP_SHORTCUT) &&
                    PoppableType.WIDGET !in popupData
            ) {
                val shortcuts =
                    mutableListOf(
                        popupDataSource.removePopupData,
                        popupDataSource.widgetsPopupData,
                        popupDataSource.appInfoPopupData,
                        popupDataSource.installPopupData,
                        popupDataSource.dontSuggestAppPopupData,
                    )
                if (Flags.enablePrivateSpaceInstallShortcut()) {
                    shortcuts.add(popupDataSource.privateProfileInstallPopupData)
                }
                if (Flags.enablePrivateSpace()) {
                    shortcuts.add(popupDataSource.uninstallAppPopupData)
                }
                if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
                    shortcuts.add(popupDataSource.bubblePopupData)
                }
                popupData[PoppableType.APP] = shortcuts.stream()
            }
        }
    }
}
