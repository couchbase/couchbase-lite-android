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
import java.util.Locale;

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

    // Null or Missing:

    public Expression isNullOrMissing() {
        return new UnaryExpression(this, UnaryExpression.OpType.Null)
                .or(new UnaryExpression(this, UnaryExpression.OpType.Missing));
    }

    public Expression notNullOrMissing() {
        return negated(isNullOrMissing());
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


    // Quantified operators:
    public static Expression variable(String name) {
        return new VariableExpression(name);
    }

    public static Expression.In any(String variable) {
        return new Expression.In(QuantifiesType.ANY, variable);
    }

    public static Expression.In every(String variable) {
        return new Expression.In(QuantifiesType.EVERY, variable);
    }

    public static Expression.In anyAndEvery(String variable) {
        return new Expression.In(QuantifiesType.ANY_AND_EVERY, variable);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[json=%s]", getClass().getSimpleName(), asJSON());
    }

    protected abstract Object asJSON();

    protected Object jsonValue(Object value) {
        if (value instanceof Expression)
            return ((Expression) value).asJSON();
        else
            return value;
    }

    static class AggregateExpression extends Expression {
        private List<Object> expressions;

        AggregateExpression(List<Object> expressions) {
            this.expressions = expressions;
        }

        public List<Object> getExpressions() {
            return expressions;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            json.add(new ArrayList<>());
            for (Object exp : expressions)
                json.add(jsonValue(exp));
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

            json.add(jsonValue((lhs)));

            if (type == OpType.Between) {
                // "between"'s RHS is an aggregate of the min and max, but the min and max need to be
                // written out as parameters to the BETWEEN operation:
                List<Object> rangeExprs = ((AggregateExpression) rhs).getExpressions();
                json.add(jsonValue((rangeExprs.get(0))));
                json.add(jsonValue((rangeExprs.get(1))));
            } else
                json.add(jsonValue((rhs)));

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
        final static String kCBLAllPropertiesName = "";


        private final String keyPath;
        private String columnName;
        private final String from; // Data Source Alias

        PropertyExpression(String keyPath) {
            this.keyPath = keyPath;
            this.from = null;
        }

        private PropertyExpression(String keyPath, String from) {
            this.keyPath = keyPath;
            this.from = from;
        }

        private PropertyExpression(String keyPath, String columnName, String from) {
            this.keyPath = keyPath;
            this.columnName = columnName;
            this.from = from;
        }

        public Expression from(String alias) {
            return new PropertyExpression(this.keyPath, alias);
        }

        static PropertyExpression allFrom(String from) {
            // Use data source alias name as the column name if specified:
            String colName = from != null ? from : kCBLAllPropertiesName;
            return new PropertyExpression(kCBLAllPropertiesName, colName, from);
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

        String getKeyPath() {
            return keyPath;
        }

        String getColumnName() {
            if (columnName == null) {
                String[] pathes = keyPath.split("\\.");
                columnName = pathes[pathes.length - 1];
            }
            return columnName;
        }
    }

    static final class UnaryExpression extends Expression {
        enum OpType {
            Missing,
            NotMissing,
            NotNull,
            Null
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
            Object opd;
            if (operand instanceof Expression)
                opd = ((Expression) operand).asJSON();
            else
                opd = operand;


            switch (type) {
                case Missing: {
                    List<Object> values = new ArrayList<>();
                    values.add("MISSING");
                    return Arrays.asList("IS", opd, values);
                }
                case NotMissing: {
                    List<Object> values = new ArrayList<>();
                    values.add("MISSING");
                    return Arrays.asList("IS NOT", opd, values);
                }
                case Null: {
                    return Arrays.asList("IS", opd, null);
                }
                case NotNull: {
                    return Arrays.asList("IS NOT", opd, null);
                }
                default:
                    return Arrays.asList(); // should't happend
            }
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

    enum QuantifiesType {
        ANY,
        ANY_AND_EVERY,
        EVERY
    }

    static final class In {
        QuantifiesType type;
        String variable;

        In(QuantifiesType type, String variable) {
            this.type = type;
            this.variable = variable;
        }

        Satisfies in(Object expression) {
            return new Satisfies(type, variable, expression);
        }
    }

    static final class Satisfies {
        QuantifiesType type;
        String variable;
        Object inExpression;

        Satisfies(QuantifiesType type, String variable, Object inExpression) {
            this.type = type;
            this.variable = variable;
            this.inExpression = inExpression;
        }

        Expression satisfies(Expression expression) {
            return new QuantifiedExpression(type, variable, inExpression, expression);
        }
    }

    static final class QuantifiedExpression extends Expression {
        QuantifiesType type;
        String variable;
        Object inExpression;
        Expression satisfiedExpression;

        QuantifiedExpression(QuantifiesType type, String variable, Object inExpression, Expression satisfiesExpression) {
            this.type = type;
            this.variable = variable;
            this.inExpression = inExpression;
            this.satisfiedExpression = satisfiesExpression;
        }

        @Override
        protected Object asJSON() {
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

    static final class VariableExpression extends Expression {
        String name;

        VariableExpression(String name) {
            this.name = name;
        }

        @Override
        protected Object asJSON() {
            List<Object> json = new ArrayList<>();
            json.add("?" + name);
            return json;
        }
    }
}
