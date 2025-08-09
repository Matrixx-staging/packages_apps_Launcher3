/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.model

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process.myUserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.AppFilter
import com.android.launcher3.Flags
import com.android.launcher3.Flags.FLAG_ENABLE_PRIVATE_SPACE
import com.android.launcher3.LauncherSettings
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.IconCache
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.model.repository.AppsListRepository
import com.android.launcher3.model.tasks.ModelRepoTestEx.trackUpdate
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyAndGetItemsUpdated
import com.android.launcher3.model.tasks.ModelRepoTestEx.verifyDelete
import com.android.launcher3.model.tasks.PackageUpdatedTask
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_ADD
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_REMOVE
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_SUSPEND
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_UNAVAILABLE
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_UNSUSPEND
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_UPDATE
import com.android.launcher3.model.tasks.PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PackageUpdatedTaskTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val context = SandboxApplication()
    @get:Rule val mockito = MockitoJUnit.rule()

    private val mUser = myUserHandle()

    @Mock lateinit var expectedActivityInfo: LauncherActivityInfo
    @Mock lateinit var mockIconCache: IconCache
    @Mock lateinit var mockAppFilter: AppFilter

    @Mock lateinit var mockApplicationInfo: ApplicationInfo
    @Mock lateinit var mockActivityInfo: ActivityInfo

    private val expectedPackage = "Test.Package"
    private val expectedComponent = ComponentName(expectedPackage, "TestClass")
    private val expectedWorkspaceItem =
        WorkspaceItemInfo().apply {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
            container = LauncherSettings.Favorites.CONTAINER_DESKTOP
            user = mUser
            intent = AppInfo.makeLaunchIntent(expectedComponent)
        }

    private lateinit var mockTaskController: ModelTaskController

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    @Before
    fun setup() {
        val appsListRepo = AppsListRepository()
        context.initDaggerComponent(
            DaggerPackageUpdatedTaskTest_TestComponent.builder()
                .bindAppsRepository(appsListRepo)
                .bindAllAppsList(spy(AllAppsList(mockIconCache, mockAppFilter) { appsListRepo }))
                .bindAppFilter(mockAppFilter)
                .bindIconCache(mockIconCache)
        )

        context.spyService(LauncherApps::class.java).apply {
            whenever(getActivityList(expectedPackage, mUser))
                .thenReturn(listOf(expectedActivityInfo))
            whenever(isPackageEnabled(expectedPackage, mUser)).thenReturn(true)
        }

        mockTaskController = spy((context.appComponent as TestComponent).getTaskController())

        whenever(mockAppFilter.shouldShowApp(expectedComponent)).thenReturn(true)
        mockApplicationInfo.apply {
            uid = 1
            isArchived = false
        }
        mockActivityInfo.isArchived = false
        expectedActivityInfo.apply {
            whenever(applicationInfo).thenReturn(mockApplicationInfo)
            whenever(activityInfo).thenReturn(mockActivityInfo)
            whenever(componentName).thenReturn(expectedComponent)
        }
    }

    private fun executeTask(op: Int) =
        TestUtil.runOnExecutorSync(Executors.MODEL_EXECUTOR) {
            PackageUpdatedTask(op, mUser, expectedPackage)
                .execute(mockTaskController, modelState.dataModel, modelState.appsList)
        }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_ADD triggers model callbacks and adds new items to AllAppsList`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val appUpdates = modelState.appsRepo.appsListStateRef.trackUpdate()
        val widgetsUpdates = modelState.homeRepo.allWidgets.trackUpdate()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_ADD)

        verify(mockIconCache).updateIconsForPkg(expectedPackage, mUser)
        assertThat(appUpdates).hasSize(2)
        assertThat(widgetsUpdates).hasSize(2)
        workspaceUpdates.verifyItemUpdated()

        verify(modelState.appsList).addPackage(context, expectedPackage, mUser)
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))
        verify(mockTaskController).bindUpdatedWidgets(modelState.dataModel)

        assertThat(modelState.appsList.data.firstOrNull()?.componentName)
            .isEqualTo(AppInfo(context, expectedActivityInfo, mUser).componentName)
        assertThat(modelState.appsRepo.appsListStateRef.value.apps.firstOrNull()?.componentName)
            .isEqualTo(AppInfo(context, expectedActivityInfo, mUser).componentName)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_UPDATE triggers model callbacks and updates items in AllAppsList`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val appUpdates = modelState.appsRepo.appsListStateRef.trackUpdate()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_UPDATE)

        verify(mockIconCache).updateIconsForPkg(expectedPackage, mUser)
        assertThat(appUpdates).hasSize(2)
        workspaceUpdates.verifyItemUpdated()

        verify(modelState.appsList).updatePackage(context, expectedPackage, mUser)
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        assertThat(modelState.appsList.data.firstOrNull()?.componentName)
            .isEqualTo(AppInfo(context, expectedActivityInfo, mUser).componentName)
        assertThat(modelState.appsRepo.appsListStateRef.value.apps.firstOrNull()?.componentName)
            .isEqualTo(AppInfo(context, expectedActivityInfo, mUser).componentName)
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_REMOVE triggers model callbacks and removes packages and icons`() {
        modelState.appsList.add(AppInfo(context, expectedActivityInfo, mUser), expectedActivityInfo)
        modelState.appsList.getAndResetChangeFlag()
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val appUpdates = modelState.appsRepo.appsListStateRef.trackUpdate()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_REMOVE)

        verify(mockIconCache).removeIconsForPkg(expectedPackage, mUser)
        assertThat(appUpdates).hasSize(2)
        workspaceUpdates.verifyItemUpdated(updateIndex = 1, totalUpdates = 3)
        workspaceUpdates.verifyDelete(deleteIndex = 2, totalUpdates = 3)

        verify(modelState.appsList).removePackage(expectedPackage, mUser)
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        assertThat(modelState.appsList.data).isEmpty()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_UNAVAILABLE triggers model callbacks and removes package from AllAppsList`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()
        executeTask(OP_UNAVAILABLE)

        workspaceUpdates.verifyItemUpdated()
        verify(modelState.appsList).removePackage(expectedPackage, mUser)
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        assertThat(modelState.appsList.data).isEmpty()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_SUSPEND triggers model callbacks and updates flags in AllAppsList`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.appsList.add(AppInfo(context, expectedActivityInfo, mUser), expectedActivityInfo)
        modelState.appsList.getAndResetChangeFlag()
        doAnswer {}.whenever(mockTaskController).bindApplicationsIfNeeded()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_SUSPEND)

        workspaceUpdates.verifyItemUpdated()
        verify(mockTaskController).bindUpdatedWorkspaceItems(listOf(expectedWorkspaceItem))

        verify(modelState.appsList).updateDisabledFlags(any(), any())
        assertThat(modelState.appsList.getAndResetChangeFlag()).isTrue()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isNotEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_UNSUSPEND triggers no callbacks when app not suspended`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.appsList.getAndResetChangeFlag()
        doAnswer {}.whenever(mockTaskController).bindApplicationsIfNeeded()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_UNSUSPEND)

        verify(mockTaskController).bindUpdatedWorkspaceItems(emptyList())
        assertThat(workspaceUpdates).hasSize(1)

        verify(modelState.appsList).updateDisabledFlags(any(), any())
        assertThat(modelState.appsList.getAndResetChangeFlag()).isFalse()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE, Flags.FLAG_MODEL_REPOSITORY)
    fun `OP_USER_AVAILABILITY_CHANGE triggers no callbacks if current user not work or private`() {
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.dataModel.addItem(context, expectedWorkspaceItem)
        modelState.appsList.getAndResetChangeFlag()
        val workspaceUpdates = modelState.homeRepo.workspaceState.trackUpdate()

        executeTask(OP_USER_AVAILABILITY_CHANGE)
        assertThat(workspaceUpdates).hasSize(1)

        verify(modelState.appsList).updateDisabledFlags(any(), any())
        verify(mockTaskController).bindUpdatedWorkspaceItems(emptyList())
        assertThat(modelState.appsList.data).isEmpty()
        assertThat(modelState.appsRepo.appsListStateRef.value.apps).isEmpty()
    }

    private fun List<WorkspaceData>.verifyItemUpdated(updateIndex: Int = 1, totalUpdates: Int = 2) =
        assertThat(verifyAndGetItemsUpdated(updateIndex, totalUpdates))
            .containsExactly(expectedWorkspaceItem)

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getTaskController(): ModelTaskController

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindAppsRepository(appsListRepo: AppsListRepository): Builder

            @BindsInstance fun bindAppFilter(appFilter: AppFilter): Builder

            @BindsInstance fun bindIconCache(iconCache: IconCache): Builder

            @BindsInstance fun bindAllAppsList(list: AllAppsList): Builder

            override fun build(): TestComponent
        }
    }
}
