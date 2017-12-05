package com.couchbase.lite.query;

import com.couchbase.lite.Query;
import com.couchbase.lite.ResultSet;

public class QueryChange {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Query query;
    private ResultSet rows;
    private Throwable error; // TODO: CouchbaseLiteException????

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    public QueryChange(Query query, ResultSet rows, Throwable error) {
        this.query = query;
        this.rows = rows;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public Query getQuery() {
        return query;
    }

    public ResultSet getRows() {
        return rows;
    }

    public Throwable getError() {
        return error;
    }
}
