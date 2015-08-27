package com.couchbase.lite.android;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Status;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;

/**
 * Android SQLiteStorageEngineFactory implementation
 */
public class AndroidSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {
    private AndroidContext context;
    
    public AndroidSQLiteStorageEngineFactory(AndroidContext context) {
        this.context = context;
    }

    @Override
    public SQLiteStorageEngine createStorageEngine(boolean enableEncryption)
            throws CouchbaseLiteException {
        if (enableEncryption)
            if (hasSQLCipher())
                return new AndroidSQLCipherStorageEngine(context);
            else
                throw new CouchbaseLiteException(
                        "Encryption not availabe (app not built with SQLCipher)",
                        Status.NOT_IMPLEMENTED);
        else
            return new AndroidSQLiteStorageEngine(context);
    }

    private boolean hasSQLCipher() {
        Class sqlCipher = null;
        try {
            sqlCipher = Class.forName("net.sqlcipher.database.SQLiteDatabase");
        } catch (ClassNotFoundException e) { }
        return (sqlCipher != null);
    }
}
