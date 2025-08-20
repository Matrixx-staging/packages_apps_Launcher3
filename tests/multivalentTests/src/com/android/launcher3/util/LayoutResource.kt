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

package com.android.launcher3.util

import android.content.Context
import com.android.launcher3.LauncherAppState
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.BgDataModel
import org.junit.rules.ExternalResource

/**
 * This uses the LauncherProvider API to import a workspace layout into the launcher under test's db
 */
class LayoutResource(private val ctx: Context) : ExternalResource() {
    // Internally, LauncherModel.rebindCallbacks() is called to load the updated data into in-memory
    // data model. This will short-circuit (and not load the new data) if called without callbacks.
    private var callbacks: BgDataModel.Callbacks? = null

    override fun before() {
        // Internally LayoutExportImportHelper uses a secure setting to set the launcher's layout
        TestUtil.grantWriteSecurePermission()
    }

    override fun after() {
        set("")
        callbacks?.let { LauncherAppState.getInstance(ctx).model.removeCallbacks(it) }
    }

    fun withCallbacks(cb: BgDataModel.Callbacks): LayoutResource {
        val model = LauncherAppState.getInstance(ctx).model
        callbacks?.let { model.removeCallbacks(it) }
        model.addCallbacks(cb)
        callbacks = cb
        return this
    }

    fun set(builder: LauncherLayoutBuilder) = set(builder.build())

    private fun set(xmlRepresentation: String) {
        callbacks ?: withCallbacks(NO_OP_CALLBACKS)
        ctx.appComponent.layoutImportExportHelper.importModelFromXml(xmlRepresentation)
    }

    companion object {
        @JvmStatic val NO_OP_CALLBACKS = object : BgDataModel.Callbacks {}
    }
}
