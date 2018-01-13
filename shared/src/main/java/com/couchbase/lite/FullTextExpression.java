package com.couchbase.lite;

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
     * @param text The search text
     * @return The full-text match expression
     */
    public Expression match(String text) {
        return new FullTextMatchExpression(this.name, text);
    }
}
