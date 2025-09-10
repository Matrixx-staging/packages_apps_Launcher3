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

package com.android.launcher3.homescreenfiles

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.SafeCloseable
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesProviderTest {
    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    private val lifeCycleTracker: DaggerSingletonTracker =
        mock() {
            on { addCloseable(any()) } doAnswer
                {
                    lifecycleCloseables.add(it.getArgument<SafeCloseable>(0))
                    Unit
                }

            on { close() } doAnswer { lifecycleCloseables.forEach { it.close() } }
        }

    private lateinit var provider: HomeScreenFilesProvider

    private val lifecycleCloseables = mutableListOf<SafeCloseable>()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.contentResolver).thenReturn(contentResolver)
        provider =
            HomeScreenFilesMediaStoreProvider(
                context,
                MoreExecutors.newDirectExecutorService(),
                lifeCycleTracker,
            )
    }

    @Test
    fun testQueriesMediaStore() {
        val expectedUri = Uri.parse("content://media/external/file")
        val expectedProjection =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA,
            )

        whenever(
                contentResolver.query(
                    eq(expectedUri),
                    eq(expectedProjection),
                    any(),
                    any(),
                    isNull(),
                    isNull(),
                )
            )
            .thenAnswer {
                val answer = MatrixCursor(expectedProjection)
                answer.addRow(
                    arrayOf("1", "test.png", "image/png", "/storage/emulated/0/Desktop/test.png")
                )
                answer.addRow(
                    arrayOf("2", "subfolder", null, "/storage/emulated/0/Desktop/subfolder")
                )
                return@thenAnswer answer
            }

        val result = provider.query().value
        assertThat(result.size).isEqualTo(2)

        val uri1 = Uri.parse("content://media/external/file/1")
        assertThat(result.containsKey(uri1)).isTrue()
        assertThat(result[uri1]!!.displayName).isEqualTo("test.png")
        assertThat(result[uri1]!!.mimeType).isEqualTo("image/png")

        val uri2 = Uri.parse("content://media/external/file/2")
        assertThat(result.containsKey(uri2)).isTrue()
        assertThat(result[uri2]!!.displayName).isEqualTo("subfolder")
        assertThat(result[uri2]!!.mimeType).isNull()

        verify(contentResolver, times(1))
            .query(
                eq(expectedUri),
                eq(expectedProjection),
                eq("${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"),
                argThat { x -> x.contentDeepEquals(arrayOf("Home screen/")) },
                isNull(),
                isNull(),
            )
    }

    @Test
    fun testRegistersChangeCallback() {
        whenever(
                contentResolver.query(
                    eq(Uri.parse("content://media/external/file/1")),
                    any(),
                    any(),
                    any(),
                    isNull(),
                    isNull(),
                )
            )
            .thenAnswer {
                val answer =
                    MatrixCursor(
                        arrayOf(
                            MediaStore.Files.FileColumns.DISPLAY_NAME,
                            MediaStore.Files.FileColumns.MIME_TYPE,
                            MediaStore.Files.FileColumns.DATA,
                        )
                    )
                answer.addRow(
                    arrayOf("NEW_test.png", "image/png", "/storage/emulated/0/Desktop/test.png")
                )
                return@thenAnswer answer
            }

        val callback = mock<(HomeScreenFilesProvider.FileChange) -> Unit>()
        val immediateExecutor = Executor { r -> r.run() }
        val unregisterChangeCallback =
            provider.fileChanges.forEach(immediateExecutor) { callback(it) }
        val underlyingContentObserverCaptor = argumentCaptor<ContentObserver>()
        verify(contentResolver, times(1))
            .registerContentObserver(
                eq(Uri.parse("content://media/external/file")),
                eq(true),
                underlyingContentObserverCaptor.capture(),
            )

        underlyingContentObserverCaptor.firstValue.dispatchChange(
            false,
            Uri.parse("content://media/external/file/1"),
            ContentResolver.NOTIFY_INSERT,
        )

        val fileChangeCaptor = argumentCaptor<HomeScreenFilesProvider.FileChange>()
        verify(callback, times(1))(fileChangeCaptor.capture())
        val fileChange = fileChangeCaptor.firstValue
        assertThat(fileChange.uri).isEqualTo(Uri.parse("content://media/external/file/1"))
        assertThat(fileChange.flags).isEqualTo(ContentResolver.NOTIFY_INSERT)
        assertThat(fileChange.file.get()!!.displayName).isEqualTo("NEW_test.png")
        assertThat(fileChange.file.get()!!.mimeType).isEqualTo("image/png")
        assertThat(fileChange.file.get()!!.isDirectory).isFalse()
        assertThat(fileChange.user).isEqualTo(Process.myUserHandle())

        lifeCycleTracker.close()
        verify(contentResolver, times(1))
            .unregisterContentObserver(eq(underlyingContentObserverCaptor.firstValue))

        unregisterChangeCallback.close()
    }
}
