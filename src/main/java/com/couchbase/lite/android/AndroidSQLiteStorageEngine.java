/**
 * Created by Wayne Carter.
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite.android;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.couchbase.lite.storage.ContentValues;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.storage.SQLException;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.util.Log;
import com.couchbase.touchdb.TDCollateJSON;

import java.util.Map;

public class AndroidSQLiteStorageEngine implements SQLiteStorageEngine {
    public static final String TAG = "AndroidSQLiteStorageEngine";

    private SQLiteDatabase database;

    @Override
    public boolean open(String path) {
        if(database != null && database.isOpen()) {
            return true;
        }

        try {
            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
            TDCollateJSON.registerCustomCollators(database);
        } catch(SQLiteException e) {
            Log.e(TAG, "Error opening", e);

            if (database != null) {
                database.close();
            }

            return false;
        }

        return database.isOpen();
    }

    @Override
    public int getVersion() {
        return database.getVersion();
    }

    @Override
    public void setVersion(int version) {
        database.setVersion(version);
    }

    @Override
    public boolean isOpen() {
        return database.isOpen();
    }

    @Override
    public void beginTransaction() {
        database.beginTransaction();
    }

    @Override
    public void endTransaction() {
        database.endTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        database.setTransactionSuccessful();
    }

    @Override
    public void execSQL(String sql) throws SQLException {
        try {
            database.execSQL(sql);
        } catch (android.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        try {
            database.execSQL(sql, bindArgs);
        } catch (android.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return new SQLiteCursorWrapper(database.rawQuery(sql, selectionArgs));
    }

    @Override
    public long insert(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, nullColumnHack, _toAndroidContentValues(values));
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues, int conflictAlgorithm) {
        return database.insertWithOnConflict(table, nullColumnHack, _toAndroidContentValues(initialValues), conflictAlgorithm);
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table, _toAndroidContentValues(values), whereClause, whereArgs);
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }

    @Override
    public void close() {
        database.close();
    }

    private android.content.ContentValues _toAndroidContentValues(ContentValues values) {
        android.content.ContentValues contentValues = new android.content.ContentValues(values.size());

        for (Map.Entry<String, Object> value : values.valueSet()) {
            if (value.getValue() == null) {
                contentValues.put(value.getKey(), (String) null);
            } else if (value.getValue() instanceof String) {
                contentValues.put(value.getKey(), (String) value.getValue());
            } else if (value.getValue() instanceof Integer) {
                contentValues.put(value.getKey(), (Integer) value.getValue());
            } else if (value.getValue() instanceof Long) {
                contentValues.put(value.getKey(), (Long) value.getValue());
            } else if (value.getValue() instanceof Boolean) {
                contentValues.put(value.getKey(), (Boolean) value.getValue());
            } else if (value.getValue() instanceof byte[]) {
                contentValues.put(value.getKey(), (byte[]) value.getValue());
            }
        }

        return contentValues;
    }

    private class SQLiteCursorWrapper implements Cursor {
        private android.database.Cursor delegate;

        public SQLiteCursorWrapper(android.database.Cursor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean moveToNext() {
            return delegate.moveToNext();
        }

        @Override
        public boolean isAfterLast() {
            return delegate.isAfterLast();
        }

        @Override
        public String getString(int columnIndex) {
            return delegate.getString(columnIndex);
        }

        @Override
        public int getInt(int columnIndex) {
            return delegate.getInt(columnIndex);
        }

        @Override
        public long getLong(int columnIndex) {
            return delegate.getLong(columnIndex);
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            return delegate.getBlob(columnIndex);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
