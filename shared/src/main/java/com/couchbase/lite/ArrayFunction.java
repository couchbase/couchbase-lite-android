package com.couchbase.lite;

import java.util.Arrays;

/**
 * Function provies array functions.
 */
public final class ArrayFunction {
    private ArrayFunction() {
    }

    /**
     * Creates an ARRAY_CONTAINS(expr, value) function that checks whether the given array
     * expression contains the given value or not.
     *
     * @param expression The expression that evluates to an array.
     * @param value      The value to search for in the given array expression.
     * @return The ARRAY_CONTAINS(expr, value) function.
     */
    public static Expression contains(Object expression, Object value) {
        return new Expression.FunctionExpresson("ARRAY_CONTAINS()", Arrays.asList(expression, value));
    }

    /**
     * Creates an ARRAY_LENGTH(expr) function that returns the length of the given array
     * expression.
     *
     * @param expression The expression that evluates to an array.
     * @return The ARRAY_LENGTH(expr) function.
     */
    public static Expression length(Object expression) {
        return new Expression.FunctionExpresson("ARRAY_LENGTH()", Arrays.asList(expression));
    }
}
