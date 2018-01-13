package com.couchbase.lite;

/**
 * Array expression
 */
public final class ArrayExpression {
    enum QuantifiesType {
        ANY,
        ANY_AND_EVERY,
        EVERY
    }

    private ArrayExpression() {

    }

    /**
     * Creates an ANY Quantified operator (ANY <variable name> IN <expr> SATISFIES <expr>)
     * with the given variable name. The method returns an IN clause object that is used for
     * specifying an array object or an expression evaluated as an array object, each item of
     * which will be evaluated against the satisfies expression.
     * The ANY operator returns TRUE if at least one of the items in the array satisfies the given
     * satisfies expression.
     *
     * @param variable The variable name.
     * @return An In object
     */
    public static ArrayExpressionIn any(String variable) {
        return new ArrayExpressionIn(QuantifiesType.ANY, variable);
    }

    /**
     * Creates an EVERY Quantified operator (EVERY <variable name> IN <expr> SATISFIES <expr>)
     * with the given variable name. The method returns an IN clause object
     * that is used for specifying an array object or an expression evaluated as an array object,
     * each of which will be evaluated against the satisfies expression.
     * The EVERY operator returns TRUE if the array is empty OR every item in the array
     * satisfies the given satisfies expression.
     *
     * @param variable The variable name.
     * @return An In object.
     */
    public static ArrayExpressionIn every(String variable) {
        return new ArrayExpressionIn(QuantifiesType.EVERY, variable);
    }

    /**
     * Creates an ANY AND EVERY Quantified operator (ANY AND EVERY <variable name> IN <expr>
     * SATISFIES <expr>) with the given variable name. The method returns an IN clause object
     * that is used for specifying an array object or an expression evaluated as an array object,
     * each of which will be evaluated against the satisfies expression.
     * The ANY AND EVERY operator returns TRUE if the array is NOT empty, and at least one of
     * the items in the array satisfies the given satisfies expression.
     *
     * @param variable The variable name.
     * @return An In object.
     */
    public static ArrayExpressionIn anyAndEvery(String variable) {
        return new ArrayExpressionIn(QuantifiesType.ANY_AND_EVERY, variable);
    }

    /**
     * Creates a variable expression. The variable are used to represent each item in an array
     * in the quantified operators (ANY/ANY AND EVERY/EVERY <variable name> IN <expr> SATISFIES <expr>)
     * to evaluate expressions over an array.
     *
     * @param name The variable name
     * @return A variable expression
     */
    // Quantified operators:
    public static Expression variable(String name) {
        return new Expression.VariableExpression(name);
    }
}
