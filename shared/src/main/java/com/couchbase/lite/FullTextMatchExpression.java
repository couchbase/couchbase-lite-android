package com.couchbase.lite;

import java.util.Arrays;


class FullTextMatchExpression extends Expression {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String indexName = null;
    private String text = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    FullTextMatchExpression(String indexName, String text) {
        this.indexName = indexName;
        this.text = text;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    @Override
    Object asJSON() {
        return Arrays.asList("MATCH", indexName, text);
    }
}
