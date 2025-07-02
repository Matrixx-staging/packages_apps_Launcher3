/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.fallback

import android.content.Context
import android.graphics.Color
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.LauncherState.FLAG_CLOSE_POPUPS
import com.android.launcher3.R
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.BaseState.FLAG_DISABLE_RESTORE
import com.android.launcher3.uioverrides.states.OverviewModalTaskState
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.ScrimColors
import com.android.quickstep.views.RecentsViewContainer

/** State definition for Fallback recents */
open class RecentsState(@JvmField val ordinal: Int, private val mFlags: Int) :
    BaseState<RecentsState> {
    init {
        sAllStates[ordinal] = this
    }

    override fun toString() =
        when (ordinal) {
            DEFAULT_STATE_ORDINAL -> "RECENTS_DEFAULT"
            MODAL_TASK_ORDINAL -> "RECENTS_MODAL_TASK"
            BACKGROUND_APP_ORDINAL -> "RECENTS_BACKGROUND_APP"
            HOME_STATE_ORDINAL -> "RECENTS_HOME"
            BG_LAUNCHER_ORDINAL -> "RECENTS_BG_LAUNCHER"
            OVERVIEW_SPLIT_SELECT_ORDINAL -> "RECENTS_SPLIT_SELECT"
            else -> "RECENTS Unknown Ordinal-$ordinal"
        }

    override fun hasFlag(mask: Int) = (mFlags and mask) != 0

    override fun getTransitionDuration(context: ActivityContext, isToState: Boolean) = 250

    override fun getHistoryForState(previousState: RecentsState) = DEFAULT

    /**
     * For this state, how modal should over view been shown. 0 modalness means all tasks drawn, 1
     * modalness means the current task is show on its own.
     */
    fun getOverviewModalness() = if (hasFlag(FLAG_MODAL)) 1f else 0f

    fun isFullScreen() = hasFlag(FLAG_FULL_SCREEN)

    /** For this state, whether clear all button should be shown. */
    fun hasClearAllButton() = hasFlag(FLAG_CLEAR_ALL_BUTTON)

    /** For this state, whether add desk button should be shown. */
    fun hasAddDeskButton() = hasFlag(FLAG_ADD_DESK_BUTTON)

    /** For this state, whether overview actions should be shown. */
    fun hasOverviewActions() = hasFlag(FLAG_OVERVIEW_ACTIONS)

    /** For this state, whether live tile should be shown. */
    fun hasLiveTile() = hasFlag(FLAG_LIVE_TILE)

    /** For this state, what color scrim should be drawn behind overview. */
    fun getScrimColor(context: Context) =
        ScrimColors(
            /* backgroundColor= */ if (hasFlag(FLAG_SCRIM))
                Themes.getAttrColor(context, R.attr.overviewScrimColor)
            else Color.TRANSPARENT,
            /* foregroundColor= */ Color.TRANSPARENT,
        )

    open fun getOverviewScaleAndOffset(container: RecentsViewContainer) =
        floatArrayOf(NO_SCALE, NO_OFFSET)

    /** For this state, whether tasks should layout as a grid rather than a list. */
    override fun displayOverviewTasksAsGrid(deviceProfile: DeviceProfile) =
        hasFlag(FLAG_SHOW_AS_GRID) && deviceProfile.deviceProperties.isTablet

    override fun showTaskThumbnailSplash() = hasFlag(FLAG_TASK_THUMBNAIL_SPLASH)

    override fun showExplodedDesktopView() =
        hasFlag(FLAG_SHOW_EXPLODED_DESKTOP_VIEW) && Flags.enableDesktopExplodedView()

    /** True if the state has overview panel visible. */
    fun isRecentsViewVisible() = hasFlag(FLAG_RECENTS_VIEW_VISIBLE)

    private class ModalState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer): FloatArray =
            if (enableGridOnlyOverview()) {
                super.getOverviewScaleAndOffset(container)
            } else
                OverviewModalTaskState.getOverviewScaleAndOffsetForModalState(
                    container.getOverviewPanel()
                )
    }

    private class BackgroundAppState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer): FloatArray =
            com.android.launcher3.uioverrides.states.BackgroundAppState
                .getOverviewScaleAndOffsetForBackgroundState(container.getOverviewPanel())
    }

    private class LauncherState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer) =
            floatArrayOf(NO_SCALE, 1f)
    }

    companion object {
        private val FLAG_MODAL = BaseState.getFlag(0)
        private val FLAG_CLEAR_ALL_BUTTON = BaseState.getFlag(1)
        private val FLAG_FULL_SCREEN = BaseState.getFlag(2)
        private val FLAG_OVERVIEW_ACTIONS = BaseState.getFlag(3)
        private val FLAG_SHOW_AS_GRID = BaseState.getFlag(4)
        private val FLAG_SCRIM = BaseState.getFlag(5)
        private val FLAG_LIVE_TILE = BaseState.getFlag(6)
        private val FLAG_RECENTS_VIEW_VISIBLE = BaseState.getFlag(7)
        private val FLAG_TASK_THUMBNAIL_SPLASH = BaseState.getFlag(8)
        private val FLAG_ADD_DESK_BUTTON = BaseState.getFlag(9)
        private val FLAG_SHOW_EXPLODED_DESKTOP_VIEW = BaseState.getFlag(10)

        const val DEFAULT_STATE_ORDINAL = 0
        const val MODAL_TASK_ORDINAL = 1
        const val BACKGROUND_APP_ORDINAL = 2
        const val HOME_STATE_ORDINAL = 3
        const val BG_LAUNCHER_ORDINAL = 4
        const val OVERVIEW_SPLIT_SELECT_ORDINAL = 5

        private val sAllStates = arrayOfNulls<RecentsState>(6)

        @JvmField
        val DEFAULT: RecentsState =
            RecentsState(
                DEFAULT_STATE_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    FLAG_CLEAR_ALL_BUTTON or
                    FLAG_OVERVIEW_ACTIONS or
                    FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_LIVE_TILE or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_ADD_DESK_BUTTON or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )
        @JvmField
        val MODAL_TASK: RecentsState =
            ModalState(
                MODAL_TASK_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    FLAG_OVERVIEW_ACTIONS or
                    FLAG_MODAL or
                    FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_LIVE_TILE or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )
        @JvmField
        val BACKGROUND_APP: RecentsState =
            BackgroundAppState(
                BACKGROUND_APP_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    BaseState.FLAG_NON_INTERACTIVE or
                    FLAG_FULL_SCREEN or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_TASK_THUMBNAIL_SPLASH),
            )
        @JvmField val HOME: RecentsState = RecentsState(HOME_STATE_ORDINAL, 0)
        @JvmField val BG_LAUNCHER: RecentsState = LauncherState(BG_LAUNCHER_ORDINAL, 0)
        @JvmField
        val OVERVIEW_SPLIT_SELECT: RecentsState =
            RecentsState(
                OVERVIEW_SPLIT_SELECT_ORDINAL,
                (FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_CLOSE_POPUPS or
                    FLAG_DISABLE_RESTORE or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )

        /** Returns the corresponding RecentsState from ordinal provided */
        @JvmStatic fun stateFromOrdinal(ordinal: Int) = sAllStates[ordinal]!!

        private const val NO_OFFSET = 0f
        private const val NO_SCALE = 1f
    }
}
