/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.launcher3.pm

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import android.util.ArrayMap
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.android.launcher3.Utilities.ATLEAST_U
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.UserBadgeDrawable
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.UserIconInfo
import java.util.function.BiConsumer
import javax.inject.Inject

/** Class which manages a local cache of user handles to avoid system rpc */
@LauncherAppSingleton
class UserCache
@Inject
constructor(
    @ApplicationContext context: Context,
    tracker: DaggerSingletonTracker,
    private val apiWrapper: ApiWrapper,
) {
    private val userEventListeners = ArrayList<BiConsumer<UserHandle, String>>()
    private val userChangeReceiver =
        SimpleBroadcastReceiver(context, Executors.MODEL_EXECUTOR) { intent: Intent ->
            this.onUsersChanged(intent)
        }

    private var userToSerialMap: Map<UserHandle, UserIconInfo> = emptyMap()

    private var userToPreInstallAppMap: Map<UserHandle, List<String>> = emptyMap()

    init {
        Executors.MODEL_EXECUTOR.execute { this.initAsync() }
        tracker.addCloseable { userChangeReceiver.unregisterReceiverSafely() }
    }

    @WorkerThread
    private fun initAsync() {
        userChangeReceiver.register(
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            ACTION_PROFILE_ADDED,
            ACTION_PROFILE_REMOVED,
            ACTION_PROFILE_UNLOCKED,
            ACTION_PROFILE_LOCKED,
            ACTION_PROFILE_AVAILABLE,
            ACTION_PROFILE_UNAVAILABLE,
        )
        updateCache()
    }

    @AnyThread
    private fun onUsersChanged(intent: Intent) {
        Executors.MODEL_EXECUTOR.execute { this.updateCache() }
        val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER) ?: return
        val action = intent.action ?: return
        userEventListeners.forEach { it.accept(user, action) }
    }

    @WorkerThread
    private fun updateCache() {
        userToSerialMap = apiWrapper.queryAllUsers()
        userToPreInstallAppMap = fetchPreInstallApps()
    }

    @WorkerThread
    private fun fetchPreInstallApps(): MutableMap<UserHandle, List<String>> {
        val userToPreInstallApp = ArrayMap<UserHandle, List<String>>()
        userToSerialMap.forEach { (userHandle, userIconInfo) ->
            // Fetch only for private profile, as other profiles have no usages yet.
            val preInstallApp =
                if (userIconInfo.isPrivate) apiWrapper.getPreInstalledSystemPackages(userHandle)
                else ArrayList()
            userToPreInstallApp[userHandle] = preInstallApp
        }
        return userToPreInstallApp
    }

    /** Adds a listener for user additions and removals */
    fun addUserEventListener(listener: BiConsumer<UserHandle, String>): SafeCloseable {
        userEventListeners.add(listener)
        return SafeCloseable { userEventListeners.remove(listener) }
    }

    /** @see UserManager.getSerialNumberForUser */
    fun getSerialNumberForUser(user: UserHandle): Long = getUserInfo(user).userSerial

    /** Returns the user properties for the provided user or default values */
    fun getUserInfo(user: UserHandle): UserIconInfo =
        userToSerialMap[user] ?: UserIconInfo(user, UserIconInfo.TYPE_MAIN)

    /** @see UserManager.getUserForSerialNumber */
    fun getUserForSerialNumber(serialNumber: Long): UserHandle =
        userToSerialMap.firstNotNullOfOrNull { (user, info) ->
            if (serialNumber == info.userSerial) user else null
        } ?: Process.myUserHandle()

    @VisibleForTesting
    fun putToCache(userHandle: UserHandle, info: UserIconInfo) {
        userToSerialMap += userHandle to info
    }

    @VisibleForTesting
    fun putToPreInstallCache(userHandle: UserHandle, preInstalledApps: List<String>) {
        userToPreInstallAppMap += userHandle to preInstalledApps
    }

    /** @see UserManager.getUserProfiles */
    val userProfiles: List<UserHandle>
        get() = userToSerialMap.keys.toList()

    /** Returns the pre-installed apps for a user. */
    fun getPreInstallApps(user: UserHandle): List<String> =
        userToPreInstallAppMap[user] ?: emptyList()

    companion object {
        @JvmField var INSTANCE = DaggerSingletonObject { it.userCache }

        @JvmField
        val ACTION_PROFILE_ADDED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_ADDED else Intent.ACTION_MANAGED_PROFILE_ADDED

        @JvmField
        val ACTION_PROFILE_REMOVED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_REMOVED else Intent.ACTION_MANAGED_PROFILE_REMOVED

        @JvmField
        val ACTION_PROFILE_UNLOCKED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_ACCESSIBLE
            else Intent.ACTION_MANAGED_PROFILE_UNLOCKED

        @JvmField
        val ACTION_PROFILE_LOCKED =
            if (ATLEAST_U) Intent.ACTION_PROFILE_INACCESSIBLE
            else Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE

        const val ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE"
        const val ACTION_PROFILE_UNAVAILABLE = "android.intent.action.PROFILE_UNAVAILABLE"

        /** Returns an instance of UserCache bound to the context provided. */
        @JvmStatic
        fun getInstance(context: Context): UserCache {
            return INSTANCE[context]
        }

        /** Get a non-themed [UserBadgeDrawable] based on the provided [UserHandle]. */
        @JvmStatic
        fun getBadgeDrawable(context: Context, userHandle: UserHandle): UserBadgeDrawable? {
            return BitmapInfo.LOW_RES_INFO.withFlags(
                    getInstance(context).getUserInfo(userHandle).applyBitmapInfoFlags(FlagOp.NO_OP)
                )
                .getBadgeDrawable(context, false /* isThemed */) as UserBadgeDrawable?
        }
    }
}
