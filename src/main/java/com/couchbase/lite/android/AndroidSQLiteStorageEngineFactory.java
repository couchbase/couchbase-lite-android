package com.couchbase.lite.android;

import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;

/**
 * Android SQLiteStorageEngineFactory implementation
 */
public class AndroidSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {

    @Override
    public SQLiteStorageEngine createStorageEngine() {
        return new AndroidSQLiteStorageEngine();
    }
}
