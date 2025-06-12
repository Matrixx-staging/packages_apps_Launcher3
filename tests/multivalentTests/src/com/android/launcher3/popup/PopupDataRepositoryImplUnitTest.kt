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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_QSB
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for the [PopupDataRepositoryImpl] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PopupDataRepositoryImplUnitTest {
    @Test
    fun getAllPopupDataWithInvalidItemInfoShouldReturnEmptyList() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_QSB
        val popupDataRepository = PopupDataRepository.createRepository(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()
        assert(popupDataMap.isEmpty())
    }

    @Test
    fun getAllPopupDataWithEmptyItemInfoShouldReturnEmptyList() {
        val popupDataRepository = PopupDataRepository.createRepository()
        val popupDataMap = popupDataRepository.getAllPopupData()
        assert(popupDataMap.isEmpty())
    }

    @Test
    fun getAllPopupDataWithFolderShouldReturnMapContainingFolderPoppableType() {
        val itemInfo = ItemInfo()
        itemInfo.itemType = ITEM_TYPE_FOLDER
        val popupDataRepository = PopupDataRepository.createRepository(itemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()
        assert(popupDataMap.size == 1)
        assert(popupDataMap[PoppableType.FOLDER] != null)
    }

    @Test
    fun getAllPopupDataWithFolderAndWidgetShouldReturnMapContainingFolderAndWidgetPoppableType() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        val popupDataRepository =
            PopupDataRepository.createRepository(folderItemInfo, widgetItemInfo)
        val popupDataMap = popupDataRepository.getAllPopupData()
        assert(popupDataMap.size == 2)
        assert(popupDataMap[PoppableType.FOLDER] != null)
        assert(popupDataMap[PoppableType.WIDGET] != null)
    }

    @Test
    fun getPopupDataByTypeShouldBeNullIfWeDontHaveThatType() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        val popupDataRepository =
            PopupDataRepository.createRepository(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByType(PoppableType.APP_PAIR)
        assert(popupDataStream == null)
    }

    @Test
    fun getPopupDataByTypeShouldNotBeNullIfWeHaveThatType() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val widgetItemInfo = ItemInfo()
        widgetItemInfo.itemType = ITEM_TYPE_APPWIDGET
        val popupDataRepository =
            PopupDataRepository.createRepository(folderItemInfo, widgetItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByType(PoppableType.FOLDER)
        assert(popupDataStream != null)
    }

    @Test
    fun popupDataShouldHaveAllTheDataFilledIn() {
        val folderItemInfo = ItemInfo()
        folderItemInfo.itemType = ITEM_TYPE_FOLDER
        val popupDataRepository = PopupDataRepository.createRepository(folderItemInfo)
        val popupDataStream = popupDataRepository.getPopupDataByType(PoppableType.FOLDER)
        val popupData = popupDataStream?.findFirst()?.get()
        assert(popupData?.category == PopupCategory.SYSTEM_SHORTCUT_FIXED)
        assert(popupData?.iconResId == R.drawable.ic_remove_no_shadow)
        assert(popupData?.labelResId == R.string.remove_drop_target_label)
        assert(popupData?.popupAction != null)
    }
}
