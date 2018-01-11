package com.couchbase.lite;

public final class QueryChange {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Query query;
    private ResultSet rs;
    private Throwable error; // TODO: CouchbaseLiteException????

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    QueryChange(Query query, ResultSet rs, Throwable error) {
        this.query = query;
        this.rs = rs;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public Query getQuery() {
        return query;
    }

    public ResultSet getResult() {
        return rs;
    }

    public Throwable getError() {
        return error;
    }
}
