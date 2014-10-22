package com.couchbase.lite.android;

import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;
import com.couchbase.lite.storage.DefaultSQLiteStorageEngineFactory;

/**
 * Android SQLiteStorageEngineFactory implementation
 */
public class AndroidSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {

    private SQLiteStorageEngineFactory _delegate = new DefaultSQLiteStorageEngineFactory();

    @Override
    public SQLiteStorageEngine createStorageEngine() {

        SQLiteStorageEngine storageEngine = _delegate.createStorageEngine();
        if (storageEngine == null) {
            return new AndroidSQLiteStorageEngine();
        }
        return storageEngine;
        
    }
}
