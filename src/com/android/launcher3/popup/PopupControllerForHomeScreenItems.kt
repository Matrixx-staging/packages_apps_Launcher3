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
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.dragndrop.LauncherDragController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Controller for home screen items: folders, app pairs, and widgets. This controller does not
 * handle apps or app shortcuts. This controller handles actions for the popups such as showing and
 * dismissing them.
 */
class PopupControllerForHomeScreenItems<T>(
    private val popupDataRepository: PopupDataRepository,
    private val dragController: LauncherDragController,
) : PopupController<T> where T : Context, T : ActivityContext {
    override fun show(view: View): Popup {
        val itemInfo = view.tag as ItemInfo
        val activityContext: ActivityContext = ActivityContext.lookupContext<T>(view.context)
        val popup: PopupContainerWithArrow<T> =
            activityContext
                .getLayoutInflater()
                .inflate(R.layout.popup_container, activityContext.getDragLayer(), false)
                as PopupContainerWithArrow<T>
        popup.originalView = view
        addSystemShortcuts(popup, itemInfo, itemView = view, activityContext)
        dragController.addDragListener(popup)
        popup.show()

        val cellLayout = activityContext.getCellLayout(itemInfo.container, itemInfo.screenId)
        if (
            itemInfo.container == Favorites.CONTAINER_DESKTOP &&
                cellLayout != null &&
                itemInfo.itemType == Favorites.ITEM_TYPE_APPWIDGET &&
                view is LauncherAppWidgetHostView
        ) {
            AppWidgetResizeFrame.showForWidget(view, cellLayout)
        }
        return popup
    }

    private fun addSystemShortcuts(
        popup: PopupContainerWithArrow<T>,
        itemInfo: ItemInfo,
        itemView: View,
        activityContext: ActivityContext,
    ) {
        val systemShortcutContainer: ViewGroup =
            popup.inflateAndAdd(R.layout.system_shortcut_rows_container, popup)
        val popupData = popupDataRepository.getPopupDataByItemInfo(itemInfo)?.toList()
        popupData?.forEach { systemShortcut ->
            val view: DeepShortcutView =
                popup.inflateAndAdd(R.layout.system_shortcut, systemShortcutContainer)

            view.iconView.setBackgroundResource(systemShortcut.iconResId)
            view.bubbleText.setText(systemShortcut.labelResId)

            view.tag = systemShortcut
            view.setOnClickListener {
                systemShortcut.popupAction.invoke(activityContext, itemInfo, itemView)
            }
        }
    }

    override fun dismiss() {
        TODO("Not yet implemented")
    }
}
