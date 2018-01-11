package com.couchbase.lite;

import java.util.Arrays;

public final class ArrayFunction {
    private ArrayFunction() {
    }

    public static Expression contains(Object expression, Object value) {
        return new FunctionExpresson("ARRAY_CONTAINS()", Arrays.asList(expression, value));
    }

    public static Expression length(Object expression) {
        return new FunctionExpresson("ARRAY_LENGTH()", Arrays.asList(expression));
    }
}
