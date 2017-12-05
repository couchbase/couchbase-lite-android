package com.couchbase.lite.query;

import com.couchbase.lite.Expression;
import com.couchbase.lite.internal.query.expression.FullTextMatchExpression;

public class FullTextExpression {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String indexName = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextExpression(String indexName) {
        this.indexName = indexName;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------
    public static FullTextExpression index(String indexName) {
        return new FullTextExpression(indexName);
    }

    public Expression match(String text) {
        return new FullTextMatchExpression(this.indexName, text);
    }
}
