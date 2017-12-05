package com.couchbase.lite;

public class FullTextIndexItem {
    Expression expression;

    private FullTextIndexItem(Expression expression) {
        this.expression = expression;
    }

    public static FullTextIndexItem property(String property) {
        return new FullTextIndexItem(Expression.property(property));
    }
}