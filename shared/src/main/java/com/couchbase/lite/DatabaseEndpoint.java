package com.couchbase.lite;

/**
 * Database based replication target endpoint.
 */
public final class DatabaseEndpoint implements Endpoint {
    private final Database database;

    /**
     * Constructor with the database instance
     *
     * @param database
     */
    public DatabaseEndpoint(Database database) {
        if (database == null)
            throw new IllegalArgumentException("the database parameter is null.");
        this.database = database;
    }

    /**
     * Return the Database instance
     */
    public Database getDatabase() {
        return database;
    }
}
