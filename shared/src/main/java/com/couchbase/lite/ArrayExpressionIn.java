package com.couchbase.lite;

/**
 * The In class represents the IN clause object in a quantified operator (ANY/ANY AND EVERY/EVERY
 * <variable name> IN <expr> SATISFIES <expr>). The IN clause is used for specifying an array
 * object or an expression evaluated as an array object, each item of which will be evaluated
 * against the satisfies expression.
 */
public final class ArrayExpressionIn {
    private ArrayExpression.QuantifiesType type;
    private String variable;

    ArrayExpressionIn(ArrayExpression.QuantifiesType type, String variable) {
        this.type = type;
        this.variable = variable;
    }

    /**
     * Creates a Satisfies clause object with the given IN clause expression that could be an
     * array object or an expression evaluated as an array object.
     *
     * @param expression The array object or the expression evaluated as an array object.
     * @return A Satisfies object.
     */
    public ArrayExpressionSatisfies in(Object expression) {
        return new ArrayExpressionSatisfies(type, variable, expression);
    }
}
