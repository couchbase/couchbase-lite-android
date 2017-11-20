package com.couchbase.lite;

public class LiveQueryChange {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private LiveQuery query;
    private QueryResultSet rows;
    private Throwable error; // TODO: CouchbaseLiteException????

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    LiveQueryChange(LiveQuery query, QueryResultSet rows, Throwable error) {
        this.query = query;
        this.rows = rows;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public LiveQuery getQuery() {
        return query;
    }

    public QueryResultSet getRows() {
        return rows;
    }

    public Throwable getError() {
        return error;
    }
}
