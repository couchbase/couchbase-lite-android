/**
 * Created by Pasin Suriyentrakorn.
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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

import com.couchbase.lite.database.security.Key;
import com.couchbase.lite.storage.ContentValues;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.storage.SQLException;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.util.Log;

import com.couchbase.lite.database.sqlite.SQLiteDatabase;

import java.util.Map;

public class AndroidSQLCipherStorageEngine implements SQLiteStorageEngine {
    private SQLiteDatabase database;

    public AndroidSQLCipherStorageEngine() {
        // Do Nothing
    }

    @Override
    public boolean open(String path) throws SQLException {
        if(database != null && database.isOpen())
            return true;
        try {
            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
            Log.v(Log.TAG_DATABASE, "%s: Opened Android sqlite db", this);
        } catch(com.couchbase.lite.database.SQLException e) {
            Log.e(Log.TAG_DATABASE, "Unable to open the SQLite database", e);
            if (database != null)
                database.close();
            throw new SQLException(e);
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
        return database != null && database.isOpen();
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
        } catch (com.couchbase.lite.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        try {
            database.execSQL(sql, bindArgs);
        } catch (com.couchbase.lite.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return new SQLiteCursor(database.rawQuery(sql, selectionArgs));
    }

    @Override
    public long insert(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, nullColumnHack, toContentValues(values));
    }

    @Override
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        try {
            return database.insertOrThrow(table, nullColumnHack, toContentValues(values));
        } catch (com.couchbase.lite.database.SQLException e) {
            if(e instanceof com.couchbase.lite.database.sqlite.exception.SQLiteConstraintException)
                throw new SQLException(SQLException.SQLITE_CONSTRAINT, e);
            else
                throw new SQLException(e);
        }
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack,
                                     ContentValues initialValues, int conflictAlgorithm) {
        return database.insertWithOnConflict(table, nullColumnHack,
                toContentValues(initialValues), conflictAlgorithm);
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table, toContentValues(values), whereClause, whereArgs);
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }

    @Override
    public void close() {
        database.close();
    }

    @Override
    public boolean supportEncryption() {
        return true;
    }

    @Override
    public byte[] derivePBKDF2SHA256Key(String password, byte[] salt, int rounds) {
        return Key.derivePBKDF2SHA256Key(password, salt, rounds);
    }

    @Override
    public String toString() {
        return "AndroidSQLCipherStorageEngine{" +
                "database=" + Integer.toHexString(System.identityHashCode(database)) +
                "}";
    }

    private com.couchbase.lite.database.ContentValues toContentValues(ContentValues values) {
        com.couchbase.lite.database.ContentValues contentValues =
                new com.couchbase.lite.database.ContentValues(values.size());
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

    private class SQLiteCursor implements Cursor {
        private com.couchbase.lite.database.cursor.Cursor cursor;

        public SQLiteCursor(com.couchbase.lite.database.cursor.Cursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public boolean moveToNext() {
            return cursor.moveToNext();
        }

        @Override
        public boolean isAfterLast() {
            return cursor.isAfterLast();
        }

        @Override
        public String getString(int columnIndex) {
            return cursor.getString(columnIndex);
        }

        @Override
        public int getInt(int columnIndex) {
            return cursor.getInt(columnIndex);
        }

        @Override
        public long getLong(int columnIndex) {
            return cursor.getLong(columnIndex);
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            return cursor.getBlob(columnIndex);
        }

        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public boolean isNull(int columnIndex) {
            return cursor.isNull(columnIndex);
        }
    }
}
