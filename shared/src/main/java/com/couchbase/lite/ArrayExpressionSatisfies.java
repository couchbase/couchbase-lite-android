package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

public final class ArrayExpressionSatisfies {
    private ArrayExpression.QuantifiesType type;
    private String variable;
    private Object inExpression;

    ArrayExpressionSatisfies(ArrayExpression.QuantifiesType type, String variable, Object inExpression) {
        this.type = type;
        this.variable = variable;
        this.inExpression = inExpression;
    }

    public Expression satisfies(Expression expression) {
        return new QuantifiedExpression(type, variable, inExpression, expression);
    }

    private static final class QuantifiedExpression extends Expression {
        private ArrayExpression.QuantifiesType type;
        private String variable;
        private Object inExpression;
        private Expression satisfiedExpression;

        QuantifiedExpression(ArrayExpression.QuantifiesType type, String variable, Object inExpression, Expression satisfiesExpression) {
            this.type = type;
            this.variable = variable;
            this.inExpression = inExpression;
            this.satisfiedExpression = satisfiesExpression;
        }

        @Override
        Object asJSON() {
            List<Object> json = new ArrayList<>(4);

            // type
            switch (type) {
                case ANY:
                    json.add("ANY");
                    break;
                case ANY_AND_EVERY:
                    json.add("ANY AND EVERY");
                    break;
                case EVERY:
                    json.add("EVERY");
                    break;
            }

            // variable
            json.add(variable);

            // in Expression
            if (inExpression instanceof Expression)
                json.add(((Expression) inExpression).asJSON());
            else
                json.add(inExpression);

            // satisfies Expression
            json.add(satisfiedExpression.asJSON());

            return json;
        }
    }
}
