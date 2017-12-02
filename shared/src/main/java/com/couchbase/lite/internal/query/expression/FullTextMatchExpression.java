package com.couchbase.lite.internal.query.expression;

import com.couchbase.lite.Expression;

import java.util.Arrays;


public class FullTextMatchExpression extends Expression {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String indexName = null;
    private String text = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public FullTextMatchExpression(String indexName, String text) {
        this.indexName = indexName;
        this.text = text;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    @Override
    public Object asJSON() {
        return Arrays.asList("MATCH", indexName, text);
    }
}
