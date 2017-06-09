package com.couchbase.lite;

import java.net.URI;

public class ReplicatorTarget {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private URI uri;
    private Database database;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public ReplicatorTarget(URI uri) {
        if (uri == null) throw new IllegalArgumentException();
        this.uri = uri;
    }

    public ReplicatorTarget(Database database) {
        if (database == null) throw new IllegalArgumentException();
        this.database = database;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------


    public URI getUri() {
        return uri;
    }

    public Database getDatabase() {
        return database;
    }

    @Override
    public String toString() {
        if (uri != null)
            return "ReplicatorTarget{" + uri + '}';
        else
            return "ReplicatorTarget{" + database.getName() + '}';
    }
}
