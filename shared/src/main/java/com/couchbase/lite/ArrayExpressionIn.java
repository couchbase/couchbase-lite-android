package com.couchbase.lite;

public class ArrayExpressionIn {
    private ArrayExpression.QuantifiesType type;
    private String variable;

    ArrayExpressionIn(ArrayExpression.QuantifiesType type, String variable) {
        this.type = type;
        this.variable = variable;
    }

    public ArrayExpressionSatisfies in(Object expression) {
        return new ArrayExpressionSatisfies(type, variable, expression);
    }
}
