//
// Copyright (c) 2017 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//

package com.couchbase.lite;

import com.couchbase.lite.internal.utils.DateUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * An expression used for constructing a query statement.
 */
public abstract class Expression {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Expression() {

    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // Value:

    /**
     * Create value expression with given value
     *
     * @param value the value
     * @return the value expression
     */
    public static Expression value(Object value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given String value
     *
     * @param value the String value
     * @return the value expression
     */
    public static Expression string(String value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given Number value
     *
     * @param value the Number value
     * @return the value expression
     */
    public static Expression number(Number value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given integer value
     *
     * @param value the integer value
     * @return the value expression
     */
    public static Expression intValue(int value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given long value
     *
     * @param value the long value
     * @return the value expression
     */
    public static Expression longValue(long value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given float value
     *
     * @param value the float value
     * @return the value expression
     */
    public static Expression floatValue(float value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given double value
     *
     * @param value the double value
     * @return the value expression
     */
    public static Expression doubleValue(double value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given boolean value
     *
     * @param value the boolean value
     * @return the value expression
     */
    public static Expression booleanValue(boolean value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given Date value
     *
     * @param value the Date value
     * @return the value expression
     */
    public static Expression date(Date value) {
        return new ValueExpression(value);
    }

    /**
     * Creates a * expression to express all properties
     *
     * @return a property expression.
     */
    public static PropertyExpression all() {
        return new PropertyExpression(PropertyExpression.kCBLAllPropertiesName);
    }

    /**
     * Create a property expression representing the value of the given property.
     *
     * @param property the name of the property in the form of a key path.
     * @return a property expression.
     */
    public static PropertyExpression property(String property) {
        return new PropertyExpression(property);
    }

    /**
     * Creates a parameter expression with the given parameter name.
     *
     * @param name The parameter name
     * @return A parameter expression.
     */
    public static Expression parameter(String name) {
        return new ParameterExpression(name);
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    public static Expression negated(Expression expression) {
        return new CompoundExpression(Arrays.asList(expression), CompoundExpression.OpType.Not);
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    public static Expression not(Expression expression) {
        return negated(expression);
    }

    /**
     * Create a multiply expression to multiply the current expression by the given expression.
     *
     * @param expression the expression to multiply by.
     * @return a multiply expression.
     */
    public Expression multiply(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Multiply);
    }

    /**
     * Create a divide expression to divide the current expression by the given expression.
     *
     * @param expression the expression to divide by.
     * @return a divide expression.
     */
    public Expression divide(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Divide);
    }

    /**
     * Create a modulo expression to modulo the current expression by the given expression.
     *
     * @param expression the expression to modulo by.
     * @return a modulo expression.
     */
    public Expression modulo(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Modulus);
    }

    /**
     * Create an add expression to add the given expression to the current expression
     *
     * @param expression an expression to add to the current expression.
     * @return an add expression.
     */
    public Expression add(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Add);
    }

    /**
     * Create a subtract expression to subtract the given expression from the current expression.
     *
     * @param expression an expression to subtract from the current expression.
     * @return a substract expression.
     */
    public Expression subtract(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Subtract);
    }

    /**
     * Create a less than expression that evaluates whether or not the current expression
     * is less than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than expression.
     */
    public Expression lessThan(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThan);
    }

    /**
     * Create a less than or equal to expression that evaluates whether or not the current
     * expression is less than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than or equal to expression.
     */
    public Expression lessThanOrEqualTo(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThanOrEqualTo);
    }

    /**
     * Create a greater than expression that evaluates whether or not the current expression
     * is greater than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than expression.
     */
    public Expression greaterThan(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThan);
    }

    /**
     * Create a greater than or equal to expression that evaluates whether or not the current
     * expression is greater than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than or equal to expression.
     */
    public Expression greaterThanOrEqualTo(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThanOrEqualTo);
    }

    /**
     * Create an equal to expression that evaluates whether or not the current expression
     * is equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an equal to expression.
     */
    public Expression equalTo(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.EqualTo);
    }

    /**
     * Create a NOT equal to expression that evaluates whether or not the current expression
     * is not equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT equal to exprssion.
     */
    public Expression notEqualTo(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.NotEqualTo);
    }

    /**
     * Create a logical AND expression that performs logical AND operation with
     * the current expression.
     *
     * @param expression the expression to AND with the current expression.
     * @return a logical AND expression.
     */
    public Expression and(Expression expression) {
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.And);
    }

    /**
     * Create a logical OR expression that performs logical OR operation with
     * the current expression.
     *
     * @param expression the expression to OR with the current expression.
     * @return a logical OR exprssion.
     */
    public Expression or(Expression expression) {
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.Or);
    }

    /**
     * Create a Like expression that evaluates whether or not the current expression is LIKE
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a Like expression.
     */
    public Expression like(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Like);
    }

    /**
     * Create a regex match expression that evaluates whether or not the current expression
     * regex matches the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a regex match expression.
     */
    public Expression regex(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.RegexLike);
    }

    /**
     * Create an IS expression that evaluates whether or not the current expression is equal to
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS expression.
     */
    public Expression is(Expression expression) {
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Is);
    }

    /**
     * Create an IS NOT expression that evaluates whether or not the current expression is not
     * equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS NOT expression.
     */
    public Expression isNot(Expression expression) {
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
    public Expression between(Expression expression1, Expression expression2) {
        Expression aggr = new AggregateExpression(Arrays.asList(expression1, expression2));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.Between);
    }

    // Null or Missing:


    /**
     * Creates an IS NULL OR MISSING expression that evaluates whether or not the current
     * expression is null or missing.
     *
     * @return An IS NULL expression.
     */
    public Expression isNullOrMissing() {
        return new UnaryExpression(this, UnaryExpression.OpType.Null)
                .or(new UnaryExpression(this, UnaryExpression.OpType.Missing));
    }

    /**
     * Creates an IS NOT NULL OR MISSING expression that evaluates whether or not the current
     * expression is NOT null or missing.
     *
     * @return An IS NOT NULL expression.
     */
    public Expression notNullOrMissing() {
        return negated(isNullOrMissing());
    }

    // Collation:

    /**
     * Creates a Collate expression with the given Collation specification. Commonly
     * the collate expression is used in the Order BY clause or the string comparison
     * 　expression (e.g. equalTo or lessThan) to specify how the two strings are　compared.
     *
     * @param collation 　The collation object.
     * @return A Collate expression.
     */
    public Expression collate(Collation collation) {
        return new CollationExpression(this, collation);
    }

    /**
     * Create an IN expression that evaluates whether or not the current expression is in the
     * given expressions.
     *
     * @param expressions the expression array to evaluate with.
     * @return an IN expression.
     */
    public Expression in(Expression... expressions) {
        Expression aggr = new AggregateExpression(Arrays.asList(expressions));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.In);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[json=%s]", getClass().getSimpleName(), asJSON());
    }

    abstract Object asJSON();

    static final class ValueExpression extends Expression {
        private Object value;

        ValueExpression(Object value) {
            if (!isSupportedType(value))
                throw new IllegalArgumentException("The given value's type is not supported.");
            this.value = value;
        }

        @Override
        Object asJSON() {
            if (value instanceof Date)
                return DateUtils.toJson((Date) value);
            else
                return value;
        }

        private boolean isSupportedType(Object value) {
            return (value == null
                    || value instanceof String
                    || value instanceof Number   // including int, long, float, double
                    || value instanceof Boolean
                    || value instanceof Date);
        }
    }

    static final class AggregateExpression extends Expression {
        private List<Expression> expressions;

        AggregateExpression(List<Expression> expressions) {
            this.expressions = expressions;
        }

        public List<Expression> getExpressions() {
            return expressions;
        }

        @Override
        Object asJSON() {
            List<Object> json = new ArrayList<Object>();
            json.add("[]");
            for (Expression expr : expressions)
                json.add(expr.asJSON());
            return json;
        }
    }

    static final class BinaryExpression extends Expression {
        enum OpType {
            Add, Between, Divide, EqualTo, GreaterThan, GreaterThanOrEqualTo,
            In, Is, IsNot, LessThan, LessThanOrEqualTo, Like,
            Modulus, Multiply, NotEqualTo, Subtract, RegexLike,
        }

        private Expression lhs;
        private Expression rhs;
        private OpType type;

        BinaryExpression(Expression lhs, Expression rhs, OpType type) {
            this.lhs = lhs;
            this.rhs = rhs;
            this.type = type;
        }

        @Override
        Object asJSON() {
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

            json.add(lhs.asJSON());

            if (type == OpType.Between) {
                // "between"'s RHS is an aggregate of the min and max, but the min and max need to be
                // written out as parameters to the BETWEEN operation:
                List<Expression> rangeExprs = ((AggregateExpression) rhs).getExpressions();
                json.add(rangeExprs.get(0).asJSON());
                json.add(rangeExprs.get(1).asJSON());
            } else
                json.add(rhs.asJSON());

            return json;
        }
    }

    static final class CompoundExpression extends Expression {
        enum OpType {
            And,
            Or,
            Not
        }

        private OpType type;
        private List<Expression> subexpressions;

        CompoundExpression(List<Expression> subexpressions, OpType type) {
            if (subexpressions == null)
                throw new AssertionError("subexpressions is null.");
            this.type = type;
            this.subexpressions = subexpressions;
        }

        @Override
        Object asJSON() {
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
            for (Expression expr : subexpressions)
                json.add(expr.asJSON());
            return json;
        }
    }

    static final class UnaryExpression extends Expression {
        enum OpType {
            Missing,
            NotMissing,
            NotNull,
            Null
        }

        private Expression operand;
        private OpType type;

        UnaryExpression(Expression operand, OpType type) {
            if (operand == null)
                throw new AssertionError("operand is null.");
            this.operand = operand;
            this.type = type;
        }

        @Override
        Object asJSON() {
            Object opd = operand.asJSON();
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
        Object asJSON() {
            List<Object> json = new ArrayList<>();
            json.add("$" + name);
            return json;
        }
    }

    static final class CollationExpression extends Expression {
        private Expression operand;
        private Collation collation;

        CollationExpression(Expression operand, Collation collation) {
            this.operand = operand;
            this.collation = collation;
        }

        @Override
        Object asJSON() {
            List<Object> json = new ArrayList<>(3);
            json.add("COLLATE");
            json.add(collation.asJSON());
            json.add(operand.asJSON());
            return json;
        }
    }

    static final class FunctionExpresson extends Expression {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private String func = null;
        private List<Expression> params = null;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        FunctionExpresson(String func, List<Expression> params) {
            this.func = func;
            this.params = params;
        }

        //---------------------------------------------
        // public level access
        //---------------------------------------------
        @Override
        Object asJSON() {
            List<Object> json = new ArrayList<>();
            json.add(func);
            for (Expression expr : params)
                json.add(expr.asJSON());
            return json;
        }
    }
}
