package com.couchbase.lite.api;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;

public class DataManager {
    private static DataManager sharedInstance;
    private Database database;

    public static DataManager instance(DatabaseConfiguration config) {
        if (sharedInstance == null && config != null)
            sharedInstance = new DataManager(config);
        return sharedInstance;
    }

    private DataManager(DatabaseConfiguration config) {
        try {
            database = new Database("dbname", config);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            database.close();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            database.delete();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }
}
