package com.couchbase.lite;

public class ArrayExpression {
    enum QuantifiesType {
        ANY,
        ANY_AND_EVERY,
        EVERY
    }

    public static ArrayExpressionIn any(String variable) {
        return new ArrayExpressionIn(QuantifiesType.ANY, variable);
    }

    public static ArrayExpressionIn every(String variable) {
        return new ArrayExpressionIn(QuantifiesType.EVERY, variable);
    }

    public static ArrayExpressionIn anyAndEvery(String variable) {
        return new ArrayExpressionIn(QuantifiesType.ANY_AND_EVERY, variable);
    }
}
