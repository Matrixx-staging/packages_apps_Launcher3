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

package com.android.launcher3.dagger

import android.annotation.ElapsedRealtimeLong
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.android.internal.R
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger
import com.android.launcher3.icons.LauncherIconProvider
import com.android.launcher3.icons.LauncherIconProviderImpl
import com.android.launcher3.logging.StatsLogManager.StatsLogManagerFactory
import com.android.launcher3.secondarydisplay.SecondaryDisplayDelegate
import com.android.launcher3.secondarydisplay.SecondaryDisplayQuickstepDelegateImpl
import com.android.launcher3.taskbar.navbutton.AbstractNavButtonLayoutter.Companion.NAVBAR_KEY_ORDER_URI
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepWidgetHolderFactory
import com.android.launcher3.uioverrides.SystemApiWrapper
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapperImpl
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.PluginManagerWrapper
import com.android.launcher3.util.window.RefreshRateTracker
import com.android.launcher3.util.window.WindowManagerProxy
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory
import com.android.quickstep.InstantAppResolverImpl
import com.android.quickstep.LauncherRestoreEventLoggerImpl
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogCompatManagerFactory
import com.android.quickstep.util.ChoreographerFrameRateTracker
import com.android.quickstep.util.ContextualSearchStateManager
import com.android.quickstep.util.GestureExclusionManager
import com.android.quickstep.util.SystemWindowManagerProxy
import com.android.systemui.shared.system.ActivityManagerWrapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import javax.inject.Named

private object Modules {}

@Module
abstract class WindowManagerProxyModule {
    @Binds abstract fun bindWindowManagerProxy(proxy: SystemWindowManagerProxy): WindowManagerProxy
}

@Module
abstract class ActivityContextModule {
    @Binds
    abstract fun bindSecondaryDisplayDelegate(
        impl: SecondaryDisplayQuickstepDelegateImpl
    ): SecondaryDisplayDelegate
}

@Module
abstract class ApiWrapperModule {
    @Binds
    abstract fun bindStatsLogManagerFactory(
        impl: StatsLogCompatManagerFactory
    ): StatsLogManagerFactory

    @Binds abstract fun bindApiWrapper(systemApiWrapper: SystemApiWrapper): ApiWrapper

    @Binds
    abstract fun bindIconProvider(iconProviderImpl: LauncherIconProviderImpl): LauncherIconProvider

    @Binds abstract fun bindInstantAppResolver(impl: InstantAppResolverImpl): InstantAppResolver

    @Binds
    abstract fun bindRestoreEventLogger(
        impl: LauncherRestoreEventLoggerImpl
    ): LauncherRestoreEventLogger
}

@Module
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetHolderFactory(factor: QuickstepWidgetHolderFactory): WidgetHolderFactory
}

@Module
abstract class PluginManagerWrapperModule {
    @Binds
    abstract fun bindPluginManagerWrapper(impl: PluginManagerWrapperImpl): PluginManagerWrapper
}

@Module
object StaticObjectModule {

    @Provides
    @JvmStatic
    fun provideGestureExclusionManager(): GestureExclusionManager = GestureExclusionManager.INSTANCE

    @Provides
    @JvmStatic
    fun provideRefreshRateTracker(): RefreshRateTracker = ChoreographerFrameRateTracker

    @Provides
    @JvmStatic
    fun provideActivityManagerWrapper(): ActivityManagerWrapper =
        ActivityManagerWrapper.getInstance()

    @Provides
    @JvmStatic
    @ElapsedRealtimeLong
    fun provideElapsedRealTime(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @ElementsIntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideSearchEntryPointsDefault(@ApplicationContext ctx: Context): Set<Uri> =
        if (ctx.resources.getBoolean(R.bool.config_searchAllEntrypointsEnabledDefault)) {
            setOf(ContextualSearchStateManager.SEARCH_ALL_ENTRYPOINTS_ENABLED_URI)
        } else emptySet()

    @Provides
    @IntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideNavBarKeyOrderDefaults(): Uri = NAVBAR_KEY_ORDER_URI
}
