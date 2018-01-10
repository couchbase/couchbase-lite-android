package com.couchbase.lite;

public class DatabaseEndpoint implements Endpoint {
    private Database database;

    public DatabaseEndpoint(final Database database) {
        this.database = database;
    }

    public Database getDatabase() {
        return database;
    }
}
