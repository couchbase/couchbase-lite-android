package com.couchbase.lite;

public final class ValueIndexItem {
    Expression expression;

    private ValueIndexItem(Expression expression) {
        this.expression = expression;
    }

    public static ValueIndexItem property(String property) {
        return new ValueIndexItem(Expression.property(property));
    }
}
