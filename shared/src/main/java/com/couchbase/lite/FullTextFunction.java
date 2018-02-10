package com.couchbase.lite;


import java.util.Arrays;

/**
 * Full-text function.
 */
public class FullTextFunction {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextFunction() {
    }

    //---------------------------------------------
    // FTS
    //---------------------------------------------

    /**
     * Creates a full-text rank function with the given full-text index name.
     * The rank function indicates how well the current query result matches
     * the full-text query when performing the match comparison.
     *
     * @param indexName The index name.
     * @return The full-text rank function.
     */
    public static Expression rank(String indexName) {
        return new Expression.FunctionExpresson("RANK()", Arrays.asList(Expression.string(indexName)));
    }
}
