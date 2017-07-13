/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An expression used for constructing a query statement.
 */
public abstract class Expression {
    /**
     * Create a property expression representing the value of the given property.
     *
     * @param property the name of the property in the form of a key path.
     * @return a property expression.
     */
    public static PropertyExpression property(String property) {
        return new PropertyExpression(property);
    }

    public static Expression parameter(String name) {
        return new ParameterExpression(name);
    }

    public static Meta meta() {
        return new Meta();
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    public static Expression negated(Object expression) {
        return new CompoundExpression(Arrays.asList(expression), CompoundExpression.OpType.Not);
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    public static Expression not(Object expression) {
        return negated(expression);
    }

    /**
     * Create a concat expression to concatenate the current expression with the given expression.
     *
     * @param expression the expression to concatenate with.
     * @return a concat expression.
     */
    public Expression concat(Object expression) {
        throw new UnsupportedOperationException();
    }

    /**
     * Create a multiply expression to multiply the current expression by the given expression.
     *
     * @param expression the expression to multiply by.
     * @return a multiply expression.
     */
    public Expression multiply(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Multiply);
    }

    /**
     * Create a divide expression to divide the current expression by the given expression.
     *
     * @param expression the expression to divide by.
     * @return a divide expression.
     */
    public Expression divide(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Divide);
    }

    /**
     * Create a modulo expression to modulo the current expression by the given expression.
     *
     * @param expression the expression to modulo by.
     * @return a modulo expression.
     */
    public Expression modulo(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Modulus);
    }

    /**
     * Create an add expression to add the given expression to the current expression
     *
     * @param expression an expression to add to the current expression.
     * @return an add expression.
     */
    public Expression add(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Add);
    }

    /**
     * Create a subtract expression to subtract the given expression from the current expression.
     *
     * @param expression an expression to subtract from the current expression.
     * @return a substract expression.
     */
    public Expression subtract(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Subtract);
    }

    /**
     * Create a less than expression that evaluates whether or not the current expression
     * is less than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than expression.
     */
    public Expression lessThan(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThan);
    }

    /**
     * Create a NOT less than expression that evaluates whether or not the current expression
     * is not less than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT less than expression.
     */
    public Expression notLessThan(Object expression) {
        return greaterThanOrEqualTo(expression);
    }

    /**
     * Create a less than or equal to expression that evaluates whether or not the current
     * expression is less than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than or equal to expression.
     */
    public Expression lessThanOrEqualTo(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThanOrEqualTo);
    }

    /**
     * Create a NOT less than or equal to expression that evaluates whether or not the current
     * expression is not less than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT less than or equal to expression.
     */
    public Expression notLessThanOrEqualTo(Object expression) {
        return greaterThan(expression);
    }

    /**
     * Create a greater than expression that evaluates whether or not the current expression
     * is greater than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than expression.
     */
    public Expression greaterThan(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThan);
    }

    /**
     * Create a NOT greater than expression that evaluates whether or not the current expression
     * is not greater than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT greater than expression.
     */
    public Expression notGreaterThan(Object expression) {
        return lessThanOrEqualTo(expression);
    }

    /**
     * Create a greater than or equal to expression that evaluates whether or not the current
     * expression is greater than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than or equal to expression.
     */
    public Expression greaterThanOrEqualTo(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThanOrEqualTo);
    }

    /**
     * Create a NOT greater than or equal to expression that evaluates whether or not the current
     * expression is not greater than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT greater than or equal to expression.
     */
    public Expression notGreaterThanOrEqualTo(Object expression) {
        return lessThan(expression);
    }

    /**
     * Create an equal to expression that evaluates whether or not the current expression
     * is equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an equal to expression.
     */
    public Expression equalTo(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.EqualTo);
    }

    /**
     * Create a NOT equal to expression that evaluates whether or not the current expression
     * is not equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT equal to exprssion.
     */
    public Expression notEqualTo(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.NotEqualTo);
    }

    /**
     * Create a logical AND expression that performs logical AND operation with
     * the current expression.
     *
     * @param expression the expression to AND with the current expression.
     * @return a logical AND expression.
     */
    public Expression and(Object expression) {
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.And);
    }

    /**
     * Create a logical OR expression that performs logical OR operation with
     * the current expression.
     *
     * @param expression the expression to OR with the current expression.
     * @return a logical OR exprssion.
     */
    public Expression or(Object expression) {
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.Or);
    }

    /**
     * Create a Like expression that evaluates whether or not the current expression is LIKE
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a Like expression.
     */
    public Expression like(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Like);
    }

    /**
     * Create a NOT Like expression that evaluates whether or not the current expression is NOT LIKE
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT Like expression.
     */
    public Expression notLike(Object expression) {
        return Expression.negated(like(expression));
    }

    /**
     * Create a regex match expression that evaluates whether or not the current expression
     * regex matches the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a regex match expression.
     */
    public Expression regex(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.RegexLike);
    }

    /**
     * Create a NOT regex match expression that evaluates whether or not the current expression
     * not regex matches the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT regex match expression.
     */
    public Expression notRegex(Object expression) {
        return Expression.negated(regex(expression));
    }

    /**
     * Create a full text match expression that evaluates whether or not the current expression
     * full text matches the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a full text match expression.
     */
    public Expression match(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Matches);
    }

    /**
     * Create a full text NOT match expression that evaluates whether or not the current expression
     * doesn't full text match the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a full text NOT match expression.
     */
    public Expression notMatch(Object expression) {
        return Expression.negated(match(expression));
    }

    /**
     * Create an IS NULL expression that evaluates whether or not the current expression is null.
     *
     * @return an IS NULL expression.
     */
    public Expression isNull() {
        return new UnaryExpression(this, UnaryExpression.OpType.Null);
    }

    /**
     * Create an IS NOT NULL expression that evaluates whether or not the current expression is
     * not null.
     *
     * @return an IS NOT NULL expression.
     */
    public Expression notNull() {
        return new UnaryExpression(this, UnaryExpression.OpType.NotNull);
    }

    /**
     * Create an IS expression that evaluates whether or not the current expression is equal to
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS expression.
     */
    public Expression is(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Is);
    }

    /**
     * Create an IS NOT expression that evaluates whether or not the current expression is not
     * equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS NOT expression.
     */
    public Expression isNot(Object expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.IsNot);
    }

    /**
     * Create a between expression that evaluates whether or not the current expression is
     * between the given expressions inclusively.
     *
     * @param expression1 the inclusive lower bound expression.
     * @param expression2 the inclusive upper bound expression.
     * @return a between expression.
     */
    public Expression between(Object expression1, Object expression2) {
        Expression aggr = new AggregateExpression(Arrays.asList(expression1, expression2));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.Between);
    }

    /**
     * Create a NOT between expression that evaluates whether or not the current expression is
     * not between the given expressions.
     *
     * @param expression1 the lower bound expression.
     * @param expression2 the upper bound expression.
     * @return a NOT between expression.
     */
    public Expression notBetween(Object expression1, Object expression2) {
        return Expression.negated(between(expression1, expression2));
    }

    /**
     * Create an IN expression that evaluates whether or not the current expression is in the
     * given expressions.
     *
     * @param expressions the expression array to evaluate with.
     * @return an IN expression.
     */
    public Expression in(Object... expressions) {
        Expression aggr = new AggregateExpression(Arrays.asList(expressions));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.In);
    }

    /**
     * Create a NOT IN expression that evaluates whether or not the current expression is not
     * in the given expressions.
     *
     * @param expressions the expression array to evaluate with.
     * @return a NOT IN expression.
     */
    public Expression notIn(Object... expressions) {
        return Expression.negated(in(expressions));
    }

    protected abstract Object asJSON();

    static class AggregateExpression extends Expression {
        private List<Object> subexpressions;

        AggregateExpression(List<Object> subexpressions) {
            this.subexpressions = subexpressions;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            for (Object exp : subexpressions) {
                if (exp instanceof Expression)
                    json.add(((Expression) exp).asJSON());
                else
                    json.add(exp);
            }
            return json;
        }
    }

    static class BinaryExpression extends Expression {
        enum OpType {
            Add, Between, Divide, EqualTo, GreaterThan, GreaterThanOrEqualTo,
            In, Is, IsNot, LessThan, LessThanOrEqualTo, Like, Matches,
            Modulus, Multiply, NotEqualTo, Subtract, RegexLike,
        }

        private Object lhs;
        private Object rhs;
        private OpType type;

        BinaryExpression(Object lhs, Object rhs, OpType type) {
            this.lhs = lhs;
            this.rhs = rhs;
            this.type = type;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            switch (type) {
                case Add:
                    json.add("+");
                    break;
                case Between:
                    json.add("BETWEEN");
                    break;
                case Divide:
                    json.add("/");
                    break;
                case EqualTo:
                    json.add("=");
                    break;
                case GreaterThan:
                    json.add(">");
                    break;
                case GreaterThanOrEqualTo:
                    json.add(">=");
                    break;
                case In:
                    json.add("IN");
                    break;
                case Is:
                    json.add("IS");
                    break;
                case IsNot:
                    json.add("IS NOT");
                    break;
                case LessThan:
                    json.add("<");
                    break;
                case LessThanOrEqualTo:
                    json.add("<=");
                    break;
                case Like:
                    json.add("LIKE");
                    break;
                case Matches:
                    json.add("MATCH");
                    break;
                case Modulus:
                    json.add("%");
                    break;
                case Multiply:
                    json.add("*");
                    break;
                case NotEqualTo:
                    json.add("!=");
                    break;
                case RegexLike:
                    json.add("regexp_like()");
                    break;
                case Subtract:
                    json.add("-");
                    break;
            }

            if (lhs instanceof Expression)
                json.add(((Expression) lhs).asJSON());
            else
                json.add(lhs);

            if (rhs instanceof AggregateExpression)
                json.addAll((List<Object>) ((AggregateExpression) rhs).asJSON());
            else if (rhs instanceof Expression)
                json.add(((Expression) rhs).asJSON());
            else
                json.add(rhs);

            return json;
        }
    }

    static class CompoundExpression extends Expression {
        enum OpType {
            And,
            Or,
            Not
        }

        private OpType type;
        private List<Object> subexpressions;

        CompoundExpression(List<Object> subexpressions, OpType type) {
            if (subexpressions == null)
                throw new AssertionError("subexpressions is null.");
            this.type = type;
            this.subexpressions = subexpressions;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            switch (type) {
                case And:
                    json.add("AND");
                    break;
                case Or:
                    json.add("OR");
                    break;
                case Not:
                    json.add("NOT");
                    break;
            }
            for (Object exp : subexpressions) {
                if (exp instanceof Expression)
                    json.add(((Expression) exp).asJSON());
                else
                    json.add(exp);
            }
            return json;
        }
    }

    public static class PropertyExpression extends Expression {
        private final String from;
        private final String keyPath;

        PropertyExpression(String keyPath) {
            this.from = null;
            this.keyPath = keyPath;
        }

        private PropertyExpression(String from, String keyPath) {
            this.from = from;
            this.keyPath = keyPath;
        }

        public Expression from(String alias) {
            return new PropertyExpression(alias, this.keyPath);
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            if (keyPath.startsWith("rank(")) {
                json.add("rank()");
                List<String> params = new ArrayList<>();
                params.add(".");
                params.add(keyPath.substring(5, keyPath.length() - 1));
                json.add(params);
            } else {
                if (from != null)
                    json.add("." + from + "." + keyPath);
                else
                    json.add("." + keyPath);
            }
            return json;
        }
    }

    static final class UnaryExpression extends Expression {
        enum OpType {
            Missing, NotMissing, NotNull, Null
        }

        private Object operand;
        private OpType type;

        UnaryExpression(Object operand, OpType type) {
            if (operand == null)
                throw new AssertionError("operand is null.");
            this.operand = operand;
            this.type = type;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<>();
            switch (type) {
                case Missing:
                    json.add("IS MISSING");
                    break;
                case NotMissing:
                    json.add("IS NOT MISSING");
                    break;
                case NotNull:
                    json.add("IS NOT NULL");
                    break;
                case Null:
                    json.add("IS NULL");
                    break;
            }

            if (operand instanceof Expression)
                json.add(((Expression) operand).asJSON());
            else
                json.add(operand);

            return json;
        }
    }

    static final class ParameterExpression extends Expression {
        private String name;

        ParameterExpression(String name) {
            this.name = name;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<>();
            json.add("$" + name);
            return json;
        }
    }
}
