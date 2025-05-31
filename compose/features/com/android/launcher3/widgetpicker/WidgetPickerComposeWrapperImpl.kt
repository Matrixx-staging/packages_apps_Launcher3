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

package com.android.launcher3.widgetpicker

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import com.android.launcher3.R
import com.android.launcher3.widgetpicker.WidgetPickerActivity
import com.android.launcher3.compose.ComposeFacade
import com.android.launcher3.compose.core.widgetpicker.WidgetPickerComposeWrapper
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.widgetpicker.WidgetPickerComponent
import com.android.launcher3.widgetpicker.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext

/**
 * An helper that bootstraps widget picker UI (from [WidgetPickerComponent]) in to
 * [WidgetPickerActivity].
 *
 * Sets up the bindings necessary for widget picker component.
 */
class WidgetPickerComposeWrapperImpl @Inject constructor(
    private val widgetPickerComponentProvider: Provider<WidgetPickerComponent.Factory>,
    private val widgetsRepository: WidgetsRepository,
    private val widgetUsersRepository: WidgetUsersRepository,
    private val widgetAppIconsRepository: WidgetAppIconsRepository,
    @BackgroundContext
    private val backgroundContext: CoroutineContext,
    @ApplicationContext
    private val appContext: Context,
) : WidgetPickerComposeWrapper {
    override fun showAllWidgets(
        activity: WidgetPickerActivity,
    ) {
        val widgetPickerComponent = newWidgetPickerComponent()
        val callbacks = object : WidgetPickerEventListeners {
            override fun onClose() {
                activity.finish()
            }
        }

        val fullWidgetsCatalog = widgetPickerComponent.getFullWidgetsCatalog()
        val composeView = ComposeFacade.initComposeView(activity.asContext()) as ComposeView
        composeView.apply {
            setContent {
                val scope = rememberCoroutineScope()
                val view = LocalView.current

                MaterialTheme { // TODO(b/408283627): Use launcher theme.
                    val eventListeners = remember { callbacks }
                    fullWidgetsCatalog.Content(eventListeners)
                }

                DisposableEffect(view) {
                    scope.launch {
                        initializeRepositories()
                    }

                    onDispose {
                        cleanUpRepositories()
                    }
                }
            }
        }

        activity.dragLayer?.addView(composeView)
    }

    private fun newWidgetPickerComponent(): WidgetPickerComponent =
        widgetPickerComponentProvider.get()
            .build(
                widgetsRepository = widgetsRepository,
                widgetUsersRepository = widgetUsersRepository,
                widgetAppIconsRepository = widgetAppIconsRepository,
                widgetHostInfo = WidgetHostInfo(
                    appContext.resources.getString(R.string.widget_button_text)
                ),
                backgroundContext = backgroundContext
            )

    private fun initializeRepositories() {
        widgetsRepository.initialize()
        widgetUsersRepository.initialize()
        widgetAppIconsRepository.initialize()
    }

    private fun cleanUpRepositories() {
        widgetsRepository.cleanUp()
        widgetUsersRepository.cleanUp()
        widgetAppIconsRepository.cleanUp()
    }
}
