/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.text.TextUtils
import androidx.annotation.AnyThread
import java.util.function.Consumer

class SimpleBroadcastReceiver(
    private val context: Context,
    private val handler: Handler,
    private val intentConsumer: Consumer<Intent>,
) : BroadcastReceiver() {

    constructor(
        context: Context,
        looperExecutor: LooperExecutor,
        intentConsumer: Consumer<Intent>,
    ) : this(context, looperExecutor.handler, intentConsumer)

    override fun onReceive(context: Context, intent: Intent) {
        intentConsumer.accept(intent)
    }

    /** Calls [register] with null completionCallback. */
    @AnyThread
    fun register(vararg actions: String) {
        register(null, *actions)
    }

    /** Calls [register] with null completionCallback. */
    @AnyThread
    fun register(flags: Int, vararg actions: String) {
        register(null, flags, *actions)
    }

    /**
     * Register broadcast receiver. If this method is called on the same looper with mHandler's
     * looper, then register will be called synchronously. Otherwise asynchronously. This ensures
     * register happens on [handler]'s looper.
     *
     * @param completionCallback callback that will be triggered after registration is completed,
     *   caller usually pass this callback to check if states has changed while registerReceiver()
     *   is executed on a binder call.
     */
    @AnyThread
    fun register(completionCallback: Runnable?, vararg actions: String) {
        if (Looper.myLooper() == handler.looper) {
            registerInternal(context, completionCallback, *actions)
        } else {
            handler.post { registerInternal(context, completionCallback, *actions) }
        }
    }

    /** Register broadcast receiver and run completion callback if passed. */
    @AnyThread
    private fun registerInternal(
        context: Context,
        completionCallback: Runnable?,
        vararg actions: String,
    ) {
        context.registerReceiver(this, getFilter(*actions))
        completionCallback?.run()
    }

    /**
     * Same as [.register] above but with additional flags params utilizine the original [Context].
     */
    @AnyThread
    fun register(completionCallback: Runnable?, flags: Int, vararg actions: String) {
        if (Looper.myLooper() == handler.looper) {
            registerInternal(context, completionCallback, flags, *actions)
        } else {
            handler.post { registerInternal(context, completionCallback, flags, *actions) }
        }
    }

    /** Register broadcast receiver and run completion callback if passed. */
    @AnyThread
    private fun registerInternal(
        context: Context,
        completionCallback: Runnable?,
        flags: Int,
        vararg actions: String,
    ) {
        context.registerReceiver(this, getFilter(*actions), flags)
        completionCallback?.run()
    }

    /**
     * Same as [.register] above but with additional permission params utilizine the original
     * [Context].
     */
    @AnyThread
    fun register(
        completionCallback: Runnable?,
        broadcastPermission: String,
        flags: Int,
        vararg actions: String,
    ) {
        if (Looper.myLooper() == handler.looper) {
            registerInternal(context, completionCallback, broadcastPermission, flags, *actions)
        } else {
            handler.post {
                registerInternal(context, completionCallback, broadcastPermission, flags, *actions)
            }
        }
    }

    /** Register broadcast receiver with permission and run completion callback if passed. */
    @AnyThread
    private fun registerInternal(
        context: Context,
        completionCallback: Runnable?,
        broadcastPermission: String,
        flags: Int,
        vararg actions: String,
    ) {
        context.registerReceiver(this, getFilter(*actions), broadcastPermission, null, flags)
        completionCallback?.run()
    }

    /** Same as [.register] above but with pkg name. */
    @AnyThread
    fun registerPkgActions(pkg: String?, vararg actions: String) {
        if (Looper.myLooper() == handler.looper) {
            context.registerReceiver(this, getPackageFilter(pkg, *actions))
        } else {
            handler.post { context.registerReceiver(this, getPackageFilter(pkg, *actions)) }
        }
    }

    /**
     * Unregister broadcast receiver. If this method is called on the same looper with mHandler's
     * looper, then unregister will be called synchronously. Otherwise asynchronously. This ensures
     * unregister happens on [.mHandler]'s looper.
     */
    @AnyThread
    fun unregisterReceiverSafely() {
        if (Looper.myLooper() == handler.looper) {
            unregisterReceiverSafelyInternal(context)
        } else {
            handler.post { unregisterReceiverSafelyInternal(context) }
        }
    }

    /** Unregister broadcast receiver ignoring any errors. */
    @AnyThread
    private fun unregisterReceiverSafelyInternal(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            // It was probably never registered or already unregistered. Ignore.
        }
    }

    companion object {
        private const val TAG = "SimpleBroadcastReceiver"

        /**
         * Creates an intent filter to listen for actions with a specific package in the data field.
         */
        @JvmStatic
        fun getPackageFilter(pkg: String?, vararg actions: String): IntentFilter {
            val filter = getFilter(*actions)
            filter.addDataScheme("package")
            if (!TextUtils.isEmpty(pkg)) {
                filter.addDataSchemeSpecificPart(pkg, PatternMatcher.PATTERN_LITERAL)
            }
            return filter
        }

        private fun getFilter(vararg actions: String): IntentFilter {
            val filter = IntentFilter()
            for (action in actions) {
                filter.addAction(action)
            }
            return filter
        }
    }
}
