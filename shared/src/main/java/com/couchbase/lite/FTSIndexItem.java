package com.couchbase.lite;

public class FTSIndexItem {
    Expression expression;

    public FTSIndexItem(Expression expression) {
        this.expression = expression;
    }

    public static FTSIndexItem expression(Expression expression) {
        return new FTSIndexItem(expression);
    }

    public Expression getExpression() {
        return expression;
    }
}