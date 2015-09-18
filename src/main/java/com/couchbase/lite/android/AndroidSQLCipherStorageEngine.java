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

import com.couchbase.lite.storage.ContentValues;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.storage.SQLException;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.util.Log;

import com.couchbase.lite.database.sqlite.SQLiteDatabase;

import java.util.Map;

public class AndroidSQLCipherStorageEngine implements SQLiteStorageEngine {
    private static final boolean SUPPORT_ENCRYPTION = true;

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
        } catch(Throwable e) {
            Log.e(Log.TAG_DATABASE, "Unable to open the SQLite database", e);
            if (database != null)
                database.close();
            throw new SQLException(e);
        }
        return database.isOpen();
    }

    @Override
    public int getVersion() {
        try {
            return database.getVersion();
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void setVersion(int version) {
        try {
            database.setVersion(version);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean isOpen() {
        try {
            return database.isOpen();
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void beginTransaction() {
        try {
            database.beginTransaction();
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void endTransaction() {
        try {
            database.endTransaction();
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void setTransactionSuccessful() {
        try {
            database.setTransactionSuccessful();
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void execSQL(String sql) throws SQLException {
        try {
            database.execSQL(sql);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        try {
            database.execSQL(sql, bindArgs);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        try {
            return new SQLiteCursor(database.rawQuery(sql, selectionArgs));
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public long insert(String table, String nullColumnHack, ContentValues values) {
        try {
            return database.insert(table, nullColumnHack, toContentValues(values));
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack,
                                     ContentValues initialValues, int conflictAlgorithm) {
        try {
            return database.insertWithOnConflict(table, nullColumnHack,
                    toContentValues(initialValues), conflictAlgorithm);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        try {
            return database.update(table, toContentValues(values), whereClause, whereArgs);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        try {
            return database.delete(table, whereClause, whereArgs);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void close() {
        try {
            database.close();
            Log.v(Log.TAG_DATABASE, "%s: Closed Android sqlite db", this);
        } catch (Throwable e) {
            throw new SQLException(e);
        }
    }

    public boolean supportEncryption() {
        return SUPPORT_ENCRYPTION;
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
            try {
                return cursor.moveToNext();
            } catch (Exception e) {
                throw new SQLException(e.getMessage(), e);
            }
        }

        @Override
        public boolean isAfterLast() {
            try {
                return cursor.isAfterLast();
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public String getString(int columnIndex) {
            try {
                return cursor.getString(columnIndex);
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getInt(int columnIndex) {
            try {
                return cursor.getInt(columnIndex);
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public long getLong(int columnIndex) {
            try {
                return cursor.getLong(columnIndex);
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            try {
                return cursor.getBlob(columnIndex);
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void close() {
            try {
                cursor.close();
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }

        @Override
        public boolean isNull(int columnIndex) {
            try {
                return cursor.isNull(columnIndex);
            } catch (Throwable e) {
                throw new SQLException(e);
            }
        }
    }
}
