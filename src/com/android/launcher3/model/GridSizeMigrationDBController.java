/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.GridType.GRID_TYPE_NON_ONE_GRID;
import static com.android.launcher3.GridType.GRID_TYPE_ONE_GRID;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class takes care of shrinking the workspace (by maximum of one row and one column), as a
 * result of restoring from a larger device or device density change.
 */
public class GridSizeMigrationDBController {

    private static final String TAG = "GridSizeMigrationDBController";
    private static final boolean DEBUG = true;

    private GridSizeMigrationDBController() {
        // Util class should not be instantiated
    }

    /**
     * Check given a new IDP, if migration is necessary.
     */
    public static boolean needsToMigrate(Context context, InvariantDeviceProfile idp) {
        return needsToMigrate(new DeviceGridState(context), new DeviceGridState(idp));
    }

    static boolean needsToMigrate(
            DeviceGridState srcDeviceState, DeviceGridState destDeviceState) {
        boolean needsToMigrate = !destDeviceState.isCompatible(srcDeviceState);
        if (needsToMigrate) {
            Log.i(TAG, "Migration is needed. destDeviceState: " + destDeviceState
                    + ", srcDeviceState: " + srcDeviceState);
        } else {
            Log.i(TAG, "Migration is not needed. destDeviceState: " + destDeviceState
                    + ", srcDeviceState: " + srcDeviceState);
        }
        return needsToMigrate;
    }

    /**
     * @return all the workspace and hotseat entries in the db.
     */
    @VisibleForTesting
    public static List<DbEntry> readAllEntries(SQLiteDatabase db, String tableName,
            Context context) {
        DbReader dbReader = new DbReader(db, tableName, context);
        List<DbEntry> result = dbReader.loadAllWorkspaceEntries();
        result.addAll(dbReader.loadHotseatEntries());
        return result;
    }

    protected static boolean isOneGridMigration(DeviceGridState srcDeviceState,
            DeviceGridState destDeviceState) {
        return srcDeviceState.getDeviceType() != TYPE_TABLET
                && srcDeviceState.getGridType() == GRID_TYPE_NON_ONE_GRID
                && destDeviceState.getGridType() == GRID_TYPE_ONE_GRID;
    }

    static void insertEntryInDb(DatabaseHelper helper, DbEntry entry,
            String srcTableName, String destTableName, List<Integer> idsInUse) {
        int id = copyEntryAndUpdate(helper, entry, srcTableName, destTableName, idsInUse);
        if (entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                || entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR) {
            for (Set<Integer> itemIds : entry.mFolderItems.values()) {
                for (int itemId : itemIds) {
                    copyEntryAndUpdate(helper, itemId, id, srcTableName, destTableName, idsInUse);
                }
            }
        }
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper,
            DbEntry entry, String srcTableName, String destTableName, List<Integer> idsInUse) {
        return copyEntryAndUpdate(
                helper, entry, -1, -1, srcTableName, destTableName, idsInUse);
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper, int id,
            int folderId, String srcTableName, String destTableName, List<Integer> idsInUse) {
        return copyEntryAndUpdate(
                helper, null, id, folderId, srcTableName, destTableName, idsInUse);
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper, DbEntry entry, int id,
            int folderId, String srcTableName, String destTableName, List<Integer> idsInUse) {
        int newId = -1;
        Cursor c = helper.getWritableDatabase().query(srcTableName, null,
                LauncherSettings.Favorites._ID + " = '" + (entry != null ? entry.id : id) + "'",
                null, null, null, null);
        while (c.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(c, values);
            if (entry != null) {
                entry.updateContentValues(values);
            } else {
                values.put(LauncherSettings.Favorites.CONTAINER, folderId);
            }
            do {
                newId = helper.generateNewItemId();
            } while (idsInUse.contains(newId));
            values.put(LauncherSettings.Favorites._ID, newId);
            helper.getWritableDatabase().insert(destTableName, null, values);
        }
        c.close();
        return newId;
    }

    static void removeEntryFromDb(SQLiteDatabase db, String tableName, IntArray entryIds) {
        db.delete(tableName,
                Utilities.createDbSelectionQuery(LauncherSettings.Favorites._ID, entryIds), null);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static class DbReader {

        final SQLiteDatabase mDb;
        final String mTableName;
        final Context mContext;
        int mLastScreenId = -1;

        Map<Integer, List<DbEntry>> mWorkspaceEntriesByScreenId =
                new ArrayMap<>();

        public DbReader(SQLiteDatabase db, String tableName, Context context) {
            mDb = db;
            mTableName = tableName;
            mContext = context;
        }

        protected List<DbEntry> loadHotseatEntries() {
            final List<DbEntry> hotseatEntries = new ArrayList<>();
            Cursor c = queryWorkspace(
                    new String[]{
                            LauncherSettings.Favorites._ID,                  // 0
                            LauncherSettings.Favorites.ITEM_TYPE,            // 1
                            LauncherSettings.Favorites.INTENT,               // 2
                            LauncherSettings.Favorites.SCREEN},              // 3
                    LauncherSettings.Favorites.CONTAINER + " = "
                            + LauncherSettings.Favorites.CONTAINER_HOTSEAT);

            final int indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);

            IntArray entriesToRemove = new IntArray();
            while (c.moveToNext()) {
                DbEntry entry = new DbEntry();
                entry.id = c.getInt(indexId);
                entry.itemType = c.getInt(indexItemType);
                entry.screenId = c.getInt(indexScreen);

                try {
                    // calculate weight
                    switch (entry.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION: {
                            entry.mIntent = c.getString(indexIntent);
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                            int total = getFolderItemsCount(entry);
                            if (total == 0) {
                                throw new Exception("Folder is empty");
                            }
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR: {
                            int total = getFolderItemsCount(entry);
                            if (total != 2) {
                                throw new Exception("App pair contains fewer or more than 2 items");
                            }
                            break;
                        }
                        default:
                            throw new Exception("Invalid item type");
                    }
                } catch (Exception e) {
                    if (DEBUG) {
                        Log.d(TAG, "Removing item " + entry.id, e);
                    }
                    entriesToRemove.add(entry.id);
                    continue;
                }
                hotseatEntries.add(entry);
            }
            removeEntryFromDb(mDb, mTableName, entriesToRemove);
            c.close();
            return hotseatEntries;
        }

        protected List<DbEntry> loadAllWorkspaceEntries() {
            mWorkspaceEntriesByScreenId.clear();
            final List<DbEntry> workspaceEntries = new ArrayList<>();
            Cursor c = queryWorkspace(
                    new String[]{
                            LauncherSettings.Favorites._ID,                  // 0
                            LauncherSettings.Favorites.ITEM_TYPE,            // 1
                            LauncherSettings.Favorites.SCREEN,               // 2
                            LauncherSettings.Favorites.CELLX,                // 3
                            LauncherSettings.Favorites.CELLY,                // 4
                            LauncherSettings.Favorites.SPANX,                // 5
                            LauncherSettings.Favorites.SPANY,                // 6
                            LauncherSettings.Favorites.INTENT,               // 7
                            LauncherSettings.Favorites.APPWIDGET_PROVIDER,   // 8
                            LauncherSettings.Favorites.APPWIDGET_ID},        // 9
                    LauncherSettings.Favorites.CONTAINER + " = "
                            + LauncherSettings.Favorites.CONTAINER_DESKTOP);
            final int indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
            final int indexCellX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
            final int indexCellY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
            final int indexSpanX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
            final int indexSpanY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
            final int indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int indexAppWidgetProvider = c.getColumnIndexOrThrow(
                    LauncherSettings.Favorites.APPWIDGET_PROVIDER);
            final int indexAppWidgetId = c.getColumnIndexOrThrow(
                    LauncherSettings.Favorites.APPWIDGET_ID);

            IntArray entriesToRemove = new IntArray();
            WidgetManagerHelper widgetManagerHelper = new WidgetManagerHelper(mContext);
            while (c.moveToNext()) {
                DbEntry entry = new DbEntry();
                entry.id = c.getInt(indexId);
                entry.itemType = c.getInt(indexItemType);
                entry.screenId = c.getInt(indexScreen);
                mLastScreenId = Math.max(mLastScreenId, entry.screenId);
                entry.cellX = c.getInt(indexCellX);
                entry.cellY = c.getInt(indexCellY);
                entry.spanX = c.getInt(indexSpanX);
                entry.spanY = c.getInt(indexSpanY);

                try {
                    // calculate weight
                    switch (entry.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION: {
                            entry.mIntent = c.getString(indexIntent);
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET: {
                            entry.mProvider = c.getString(indexAppWidgetProvider);
                            entry.appWidgetId = c.getInt(indexAppWidgetId);
                            ComponentName cn = ComponentName.unflattenFromString(entry.mProvider);

                            LauncherAppWidgetProviderInfo pInfo = widgetManagerHelper
                                    .getLauncherAppWidgetInfo(entry.appWidgetId, cn);
                            Point spans = null;
                            if (pInfo != null) {
                                spans = pInfo.getMinSpans();
                            }
                            if (spans != null) {
                                entry.minSpanX = spans.x > 0 ? spans.x : entry.spanX;
                                entry.minSpanY = spans.y > 0 ? spans.y : entry.spanY;
                            } else {
                                // Assume that the widget be resized down to 2x2
                                entry.minSpanX = entry.minSpanY = 2;
                            }

                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                            int total = getFolderItemsCount(entry);
                            if (total == 0) {
                                throw new Exception("Folder is empty");
                            }
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR: {
                            int total = getFolderItemsCount(entry);
                            if (total != 2) {
                                throw new Exception("App pair contains fewer or more than 2 items");
                            }
                            break;
                        }
                        default:
                            throw new Exception("Invalid item type");
                    }
                } catch (Exception e) {
                    if (DEBUG) {
                        Log.d(TAG, "Removing item " + entry.id, e);
                    }
                    entriesToRemove.add(entry.id);
                    continue;
                }
                workspaceEntries.add(entry);
                if (!mWorkspaceEntriesByScreenId.containsKey(entry.screenId)) {
                    mWorkspaceEntriesByScreenId.put(entry.screenId, new ArrayList<>());
                }
                mWorkspaceEntriesByScreenId.get(entry.screenId).add(entry);
            }
            removeEntryFromDb(mDb, mTableName, entriesToRemove);
            c.close();
            return workspaceEntries;
        }

        private int getFolderItemsCount(DbEntry entry) {
            Cursor c = queryWorkspace(
                    new String[]{LauncherSettings.Favorites._ID, LauncherSettings.Favorites.INTENT},
                    LauncherSettings.Favorites.CONTAINER + " = " + entry.id);

            int total = 0;
            while (c.moveToNext()) {
                try {
                    int id = c.getInt(0);
                    String intent = c.getString(1);
                    total++;
                    if (!entry.mFolderItems.containsKey(intent)) {
                        entry.mFolderItems.put(intent, new HashSet<>());
                    }
                    entry.mFolderItems.get(intent).add(id);
                } catch (Exception e) {
                    removeEntryFromDb(mDb, mTableName, IntArray.wrap(c.getInt(0)));
                }
            }
            c.close();
            return total;
        }

        private Cursor queryWorkspace(String[] columns, String where) {
            return mDb.query(mTableName, columns, where, null, null, null, null);
        }
    }
}
