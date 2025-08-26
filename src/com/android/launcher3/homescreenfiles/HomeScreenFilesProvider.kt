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

import android.net.Uri
import com.android.launcher3.util.ListenableStream

/** Represents a single file or folder item queried by [HomeScreenFilesProvider]. */
data class HomeScreenFile(val displayName: String, val mimeType: String?, val isDirectory: Boolean)

/** An interface for managing file items to be shown on the home screen. */
interface HomeScreenFilesProvider {
    /** Returns all eligible file items to be shown on the home screen. */
    fun query(): Lazy<Map<Uri, HomeScreenFile>>

    /**
     * Information about a change to a file item shown on the home screen.
     *
     * @param uri The URI of the item that was changed and
     * @param flags The bitmask describing the type of the file change (one of
     *   [ContentResolver.NOTIFY_INSERT], [ContentResolver.NOTIFY_UPDATE],
     *   [ContentResolver.NOTIFY_DELETE]).
     */
    data class FileChange(val uri: Uri, val flags: Int)

    /** A stream of changes to file items shown on the home screen. */
    val fileChanges: ListenableStream<FileChange>
}
