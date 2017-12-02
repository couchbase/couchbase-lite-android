package com.couchbase.lite;


public class ValueIndexItem {
    Expression expression;

    public ValueIndexItem(Expression expression) {
        this.expression = expression;
    }

    public static ValueIndexItem property(String property) {
        return new ValueIndexItem(Expression.property(property));
    }
}
