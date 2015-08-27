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

import net.sqlcipher.database.SQLiteDatabase;
//import net.sqlcipher.database.SQLiteException;

public class AndroidSQLCipherStorageEngine implements SQLiteStorageEngine {
    public static final String TAG = "AndroidSQLCipherStorageEngine";

    private SQLiteDatabase database;
    private AndroidContext context;

    public AndroidSQLCipherStorageEngine(AndroidContext context) {
        this.context = context;
    }

    @Override
    public boolean open(String path, String encryptionKey) throws SQLException {
        if(database != null && database.isOpen())
            return true;

        try {
            // Write-Ahead Logging (WAL) http://sqlite.org/wal.html
            // http://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#enableWriteAheadLogging()
            // ENABLE_WRITE_AHEAD_LOGGING is available from API 16
            // enableWriteAheadLogging() is available from API 11, but it does not work with API 9 and 10.
            // Minimum version CBL Android supports is API 9

            // NOTE: Not obvious difference. But it seems Without WAL is faster.
            //       WAL consumes more memory, it might make GC busier.
            SQLiteDatabase.loadLibs(context.getWrappedContext());
            char[] key = encryptionKey != null ? encryptionKey.toCharArray() : new char[0];
            database = SQLiteDatabase.openOrCreateDatabase(path, key, null);
            Log.v(Log.TAG_DATABASE, "%s: Opened Android sqlite db", this);

            String sqliteDatabaseClassName = "net/sqlcipher/database/SQLiteDatabase";
            SQLiteJsonCollator.register(database, sqliteDatabaseClassName, 0);
            SQLiteRevCollator.register(database, sqliteDatabaseClassName, 0);
        } catch(net.sqlcipher.database.SQLiteException e) {
            Log.e(TAG, "Unable to open the SQLite database", e);
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
        return database.isOpen();
    }

    @Override
    public void beginTransaction() {
        database.beginTransaction();
        // NOTE: Use beginTransactionNonExclusive() with ENABLE_WRITE_AHEAD_LOGGING
        //       http://stackoverflow.com/questions/8104832/sqlite-simultaneous-reading-and-writing
        // database.beginTransactionNonExclusive();
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
        return database.insert(table, nullColumnHack,
                AndroidSQLiteHelper.toAndroidContentValues(values));
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack,
                                     ContentValues initialValues, int conflictAlgorithm) {
        return database.insertWithOnConflict(table, nullColumnHack,
                AndroidSQLiteHelper.toAndroidContentValues(initialValues), conflictAlgorithm);
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table,
                AndroidSQLiteHelper.toAndroidContentValues(values), whereClause, whereArgs);
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }

    @Override
    public void close() {
        database.close();
        Log.v(Log.TAG_DATABASE, "%s: Closed Android sqlite db", this);
    }

    public boolean supportEncryption() {
        return true;
    }

    @Override
    public String toString() {
        return "AndroidSQLCipherStorageEngine{" +
                "database=" + Integer.toHexString(System.identityHashCode(database)) +
                "}";
    }
}
