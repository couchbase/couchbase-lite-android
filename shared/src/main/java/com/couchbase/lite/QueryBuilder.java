package com.couchbase.lite;

public class QueryBuilder {
    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    private QueryBuilder() {
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Create a SELECT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select object.
     */
    public static Select select(SelectResult... results) {
        return new Select(false, results);
    }

    /**
     * Create a SELECT DISTINCT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select distinct object.
     */
    public static Select selectDistinct(SelectResult... results) {
        return new Select(true, results);
    }
}
