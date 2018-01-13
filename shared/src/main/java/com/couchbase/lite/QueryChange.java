package com.couchbase.lite;

/**
 * QueryChange contains the information about the query result changes reported
 * by a query object.
 */
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

    /**
     * Return the source live query object.
     */
    public Query getQuery() {
        return query;
    }

    /**
     * Return the new query result.
     */
    public ResultSet getResults() {
        return rs;
    }

    /**
     * Return the error occurred when running the query.
     */
    public Throwable getError() {
        return error;
    }
}
