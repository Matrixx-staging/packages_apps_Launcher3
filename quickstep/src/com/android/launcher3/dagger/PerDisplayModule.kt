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

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import com.android.app.displaylib.DisplayLibBackground
import com.android.app.displaylib.DisplayLibComponent
import com.android.app.displaylib.DisplayRepository
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.displaylib.SingleInstanceRepositoryImpl
import com.android.app.displaylib.createDisplayLibComponent
import com.android.launcher3.Flags.enableOverviewOnConnectedDisplays
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.RotationTouchHelper
import com.android.quickstep.TaskAnimationManager
import com.android.systemui.dagger.qualifiers.Background
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

@Module(includes = [BasePerDisplayModule::class, PerDisplayRepositoriesModule::class])
interface PerDisplayModule

@Module(includes = [DisplayLibModule::class])
interface BasePerDisplayModule {
    @Binds
    @DisplayLibBackground
    abstract fun bindDisplayLibBackground(@Background bgScope: CoroutineScope): CoroutineScope
}

@Module
object PerDisplayRepositoriesModule {
    @Provides
    @LauncherAppSingleton
    fun provideRecentsAnimationDeviceStateRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<RecentsAnimationDeviceState>,
        rotationTouchHelperRepository: PerDisplayRepository<RotationTouchHelper>,
        instanceFactory: RecentsAnimationDeviceState.Factory,
    ): PerDisplayRepository<RecentsAnimationDeviceState> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "RecentsAnimationDeviceStateRepo",
                { displayId ->
                    rotationTouchHelperRepository[displayId]?.let {
                        instanceFactory.create(displayId, it)
                    }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "RecentsAnimationDeviceStateRepo",
                rotationTouchHelperRepository[DEFAULT_DISPLAY]?.let {
                    instanceFactory.create(DEFAULT_DISPLAY, it)
                }!!, // Assert the default display is always available.
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideTaskAnimationManagerRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<TaskAnimationManager>,
        instanceFactory: TaskAnimationManager.Factory,
    ): PerDisplayRepository<TaskAnimationManager> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create("TaskAnimationManagerRepo", instanceFactory::create)
        } else {
            SingleInstanceRepositoryImpl(
                "TaskAnimationManager",
                instanceFactory.create(DEFAULT_DISPLAY),
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    fun provideRotationTouchHandlerRepo(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<RotationTouchHelper>,
        @DisplayContext displayContextRepository: PerDisplayRepository<Context>,
        instanceFactory: RotationTouchHelper.Factory,
    ): PerDisplayRepository<RotationTouchHelper> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "RotationTouchHelperRepo",
                { displayId ->
                    displayContextRepository[displayId]?.let { instanceFactory.create(it) }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "RotationTouchHelperRepo",
                instanceFactory.create(displayContextRepository[DEFAULT_DISPLAY]),
            )
        }
    }

    @Provides
    @LauncherAppSingleton
    @DisplayContext
    fun provideDisplayContext(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<Context>,
        @ApplicationContext context: Context,
        displayRepository: DisplayRepository,
    ): PerDisplayRepository<Context> {
        return if (enableOverviewOnConnectedDisplays()) {
            repositoryFactory.create(
                "DisplayContextRepo",
                { displayId ->
                    displayRepository.getDisplay(displayId)?.let {
                        context.createDisplayContext(it)
                    }
                },
            )
        } else {
            SingleInstanceRepositoryImpl(
                "DisplayContextRepo",
                context.createDisplayContext(displayRepository.getDisplay(DEFAULT_DISPLAY)!!),
            )
        }
    }
}

/**
 * Module to bind the DisplayRepository from displaylib to the LauncherAppSingleton dagger graph.
 */
@Module
object DisplayLibModule {
    @Provides
    @LauncherAppSingleton
    fun displayLibComponent(
        @ApplicationContext context: Context,
        @Background bgHandler: Handler,
        @Background bgApplicationScope: CoroutineScope,
        coroutineDispatcherProvider: DispatcherProvider,
    ): DisplayLibComponent {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        return createDisplayLibComponent(
            displayManager,
            bgHandler,
            bgApplicationScope,
            coroutineDispatcherProvider.background,
        )
    }

    @Provides
    @LauncherAppSingleton
    fun providesDisplayRepositoryFromLib(
        displayLibComponent: DisplayLibComponent
    ): DisplayRepository {
        return displayLibComponent.displayRepository
    }

    @Provides
    fun dumpRegistrationLambda(): PerDisplayRepository.InitCallback =
        PerDisplayRepository.InitCallback { debugName, _ ->
            Log.d("PerDisplayInitCallback", debugName)
        }
}
