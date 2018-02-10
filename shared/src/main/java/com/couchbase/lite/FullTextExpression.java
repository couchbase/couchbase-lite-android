package com.couchbase.lite;

import java.util.Arrays;

/**
 * Full-text expression
 */
public final class FullTextExpression {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String name = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextExpression(String name) {
        this.name = name;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    /**
     * Creates a full-text expression with the given full-text index name.
     *
     * @param name The full-text index name.
     * @return The full-text expression.
     */
    public static FullTextExpression index(String name) {
        return new FullTextExpression(name);
    }

    /**
     * Creates a full-text match expression with the given search text.
     *
     * @param query The search text
     * @return The full-text match expression
     */
    public Expression match(String query) {
        return new FullTextMatchExpression(this.name, query);
    }

    static final class FullTextMatchExpression extends Expression {
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
        // package level access
        //---------------------------------------------

        @Override
        Object asJSON() {
            return Arrays.asList("MATCH", indexName, text);
        }
    }
}
