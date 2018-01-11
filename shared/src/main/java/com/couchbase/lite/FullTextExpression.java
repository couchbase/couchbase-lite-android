package com.couchbase.lite;

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
    public static FullTextExpression index(String name) {
        return new FullTextExpression(name);
    }

    public Expression match(String text) {
        return new FullTextMatchExpression(this.name, text);
    }
}
