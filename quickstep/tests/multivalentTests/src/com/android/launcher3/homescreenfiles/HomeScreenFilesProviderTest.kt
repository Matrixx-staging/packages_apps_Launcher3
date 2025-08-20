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
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HomeScreenFilesProviderTest {
    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    private lateinit var provider: HomeScreenFilesProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.contentResolver).thenReturn(contentResolver)
        provider =
            HomeScreenFilesMediaStoreProvider(context, MoreExecutors.newDirectExecutorService())
    }

    @Test
    fun queriesMediaStore() {
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
}
