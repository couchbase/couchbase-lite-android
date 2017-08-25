package com.couchbase.lite;


public class ValueIndexItem {
    private Expression expression;

    public ValueIndexItem(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    public static ValueIndexItem expression(Expression expression) {
        return new ValueIndexItem(expression);
    }
}
