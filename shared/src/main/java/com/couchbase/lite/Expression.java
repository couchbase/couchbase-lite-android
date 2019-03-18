//
// Expression.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.couchbase.lite.internal.utils.DateUtils;


/**
 * An expression used for constructing a query statement.
 */
public abstract class Expression {
    static final class ValueExpression extends Expression {
        private Object value;

        ValueExpression(Object value) {
            if (!isSupportedType(value)) {
                throw new IllegalArgumentException("The given value's type is not supported.");
            }
            this.value = value;
        }

        @Override
        Object asJSON() {
            return asJSON(value);
        }

        private boolean isSupportedType(Object value) {
            return (value == null
                || value instanceof String
                || value instanceof Number   // including int, long, float, double
                || value instanceof Boolean
                || value instanceof Date
                || value instanceof Map
                || value instanceof List
                || value instanceof Expression);
        }

        @SuppressWarnings("unchecked")
        private Object asJSON(Object value) {
            if (value instanceof Date) { return DateUtils.toJson((Date) value); }
            else if (value instanceof Map) { return mapAsJSON((Map<String, Object>) value); }
            else if (value instanceof List) { return listAsJSON((List<Object>) value); }
            else if (value instanceof Expression) { return ((Expression) value).asJSON(); }
            else {
                if (!isSupportedType(value)) {
                    throw new IllegalArgumentException("The value type is not supported: " + value);
                }
                return value;
            }
        }

        private Object mapAsJSON(Map<String, Object> map) {
            final Map<String, Object> json = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                json.put(entry.getKey(), asJSON(entry.getValue()));
            }
            return json;
        }

        private Object listAsJSON(List<Object> list) {
            final List<Object> json = new ArrayList<>();
            json.add("[]"); // Array Operation
            for (Object obj : list) { json.add(asJSON(obj)); }
            return json;
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // Value:

    static final class AggregateExpression extends Expression {
        private final List<Expression> expressions;

        AggregateExpression(List<Expression> expressions) {
            this.expressions = expressions;
        }

        public List<Expression> getExpressions() {
            return expressions;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>();
            json.add("[]");
            for (Expression expr : expressions) { json.add(expr.asJSON()); }
            return json;
        }
    }

    static final class BinaryExpression extends Expression {
        enum OpType {
            Add, Between, Divide, EqualTo, GreaterThan, GreaterThanOrEqualTo,
            In, Is, IsNot, LessThan, LessThanOrEqualTo, Like,
            Modulus, Multiply, NotEqualTo, Subtract, RegexLike,
        }

        private final Expression lhs;
        private final Expression rhs;
        private final OpType type;

        BinaryExpression(Expression lhs, Expression rhs, OpType type) {
            this.lhs = lhs;
            this.rhs = rhs;
            this.type = type;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>();
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
                final List<Expression> rangeExprs = ((AggregateExpression) rhs).getExpressions();
                json.add(rangeExprs.get(0).asJSON());
                json.add(rangeExprs.get(1).asJSON());
            }
            else { json.add(rhs.asJSON()); }

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
            if (subexpressions == null) { throw new AssertionError("subexpressions cannot be null."); }
            this.type = type;
            this.subexpressions = subexpressions;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>();
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
            for (Expression expr : subexpressions) { json.add(expr.asJSON()); }
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
            if (operand == null) { throw new AssertionError("operand cannot be null."); }
            this.operand = operand;
            this.type = type;
        }

        @Override
        Object asJSON() {
            final List<Object> values;
            final Object opd = operand.asJSON();
            switch (type) {
                case Missing:
                    values = new ArrayList<>();
                    values.add("MISSING");
                    return Arrays.asList("IS", opd, values);

                case NotMissing:
                    values = new ArrayList<>();
                    values.add("MISSING");
                    return Arrays.asList("IS NOT", opd, values);

                case Null:
                    return Arrays.asList("IS", opd, null);

                case NotNull:
                    return Arrays.asList("IS NOT", opd, null);

                default:
                    return Arrays.asList(); // should't happend
            }
        }
    }

    static final class ParameterExpression extends Expression {
        private final String name;

        ParameterExpression(String name) {
            this.name = name;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>();
            json.add("$" + name);
            return json;
        }
    }

    static final class CollationExpression extends Expression {
        private final Expression operand;
        private final Collation collation;

        CollationExpression(Expression operand, Collation collation) {
            this.operand = operand;
            this.collation = collation;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>(3);
            json.add("COLLATE");
            json.add(collation.asJSON());
            json.add(operand.asJSON());
            return json;
        }
    }

    static final class FunctionExpression extends Expression {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private final String func;
        private final List<Expression> params;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        FunctionExpression(String func, List<Expression> params) {
            this.func = func;
            this.params = params;
        }

        @Override
        Object asJSON() {
            final List<Object> json = new ArrayList<>();
            json.add(func);
            for (Expression expr : params) { json.add(expr.asJSON()); }
            return json;
        }
    }

    /**
     * Create value expression with given value
     *
     * @param value the value
     * @return the value expression
     */
    @NonNull
    public static Expression value(Object value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given String value
     *
     * @param value the String value
     * @return the value expression
     */
    @NonNull
    public static Expression string(String value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given Number value
     *
     * @param value the Number value
     * @return the value expression
     */
    @NonNull
    public static Expression number(Number value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given integer value
     *
     * @param value the integer value
     * @return the value expression
     */
    @NonNull
    public static Expression intValue(int value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given long value
     *
     * @param value the long value
     * @return the value expression
     */
    @NonNull
    public static Expression longValue(long value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given float value
     *
     * @param value the float value
     * @return the value expression
     */
    @NonNull
    public static Expression floatValue(float value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given double value
     *
     * @param value the double value
     * @return the value expression
     */
    @NonNull
    public static Expression doubleValue(double value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given boolean value
     *
     * @param value the boolean value
     * @return the value expression
     */
    @NonNull
    public static Expression booleanValue(boolean value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with given Date value
     *
     * @param value the Date value
     * @return the value expression
     */
    @NonNull
    public static Expression date(Date value) {
        return new ValueExpression(value);
    }

    /**
     * Creates value expression with the given map.
     *
     * @param value the map value
     * @return the value expression.
     */
    @NonNull
    public static Expression map(Map<String, Object> value) {
        return new ValueExpression(value);
    }

    /**
     * Create value expression with the given list.
     *
     * @param value the list value.
     * @return the value expression.
     */
    @NonNull
    public static Expression list(List<Object> value) {
        return new ValueExpression(value);
    }

    /**
     * Creates a * expression to express all properties
     *
     * @return a property expression.
     */
    @NonNull
    public static PropertyExpression all() {
        return new PropertyExpression(PropertyExpression.kCBLAllPropertiesName);
    }

    /**
     * Create a property expression representing the value of the given property.
     *
     * @param property the name of the property in the form of a key path.
     * @return a property expression.
     */
    @NonNull
    public static PropertyExpression property(@NonNull String property) {
        if (property == null) {
            throw new IllegalArgumentException("property cannot be null.");
        }
        return new PropertyExpression(property);
    }

    /**
     * Creates a parameter expression with the given parameter name.
     *
     * @param name The parameter name
     * @return A parameter expression.
     */
    @NonNull
    public static Expression parameter(@NonNull String name) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null.");
        }
        return new ParameterExpression(name);
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    @NonNull
    public static Expression negated(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new CompoundExpression(Arrays.asList(expression), CompoundExpression.OpType.Not);
    }

    /**
     * Create a negated expression to represent the negated result of the given expression.
     *
     * @param expression the expression to be negated.
     * @return a negated expression.
     */
    @NonNull
    public static Expression not(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return negated(expression);
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Expression() { }

    /**
     * Create a multiply expression to multiply the current expression by the given expression.
     *
     * @param expression the expression to multiply by.
     * @return a multiply expression.
     */
    @NonNull
    public Expression multiply(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Multiply);
    }

    /**
     * Create a divide expression to divide the current expression by the given expression.
     *
     * @param expression the expression to divide by.
     * @return a divide expression.
     */
    @NonNull
    public Expression divide(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Divide);
    }

    /**
     * Create a modulo expression to modulo the current expression by the given expression.
     *
     * @param expression the expression to modulo by.
     * @return a modulo expression.
     */
    @NonNull
    public Expression modulo(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Modulus);
    }

    /**
     * Create an add expression to add the given expression to the current expression
     *
     * @param expression an expression to add to the current expression.
     * @return an add expression.
     */
    @NonNull
    public Expression add(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Add);
    }

    /**
     * Create a subtract expression to subtract the given expression from the current expression.
     *
     * @param expression an expression to subtract from the current expression.
     * @return a substract expression.
     */
    @NonNull
    public Expression subtract(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Subtract);
    }

    /**
     * Create a less than expression that evaluates whether or not the current expression
     * is less than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than expression.
     */
    @NonNull
    public Expression lessThan(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThan);
    }

    /**
     * Create a less than or equal to expression that evaluates whether or not the current
     * expression is less than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a less than or equal to expression.
     */
    @NonNull
    public Expression lessThanOrEqualTo(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.LessThanOrEqualTo);
    }

    /**
     * Create a greater than expression that evaluates whether or not the current expression
     * is greater than the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than expression.
     */
    @NonNull
    public Expression greaterThan(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression is null");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThan);
    }

    /**
     * Create a greater than or equal to expression that evaluates whether or not the current
     * expression is greater than or equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a greater than or equal to expression.
     */
    @NonNull
    public Expression greaterThanOrEqualTo(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.GreaterThanOrEqualTo);
    }

    /**
     * Create an equal to expression that evaluates whether or not the current expression
     * is equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an equal to expression.
     */
    @NonNull
    public Expression equalTo(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.EqualTo);
    }

    // Null or Missing:

    /**
     * Create a NOT equal to expression that evaluates whether or not the current expression
     * is not equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a NOT equal to exprssion.
     */
    @NonNull
    public Expression notEqualTo(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.NotEqualTo);
    }

    /**
     * Create a logical AND expression that performs logical AND operation with
     * the current expression.
     *
     * @param expression the expression to AND with the current expression.
     * @return a logical AND expression.
     */
    @NonNull
    public Expression and(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.And);
    }

    // Collation:

    /**
     * Create a logical OR expression that performs logical OR operation with
     * the current expression.
     *
     * @param expression the expression to OR with the current expression.
     * @return a logical OR exprssion.
     */
    @NonNull
    public Expression or(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new CompoundExpression(Arrays.asList(this, expression), CompoundExpression.OpType.Or);
    }

    /**
     * Create a Like expression that evaluates whether or not the current expression is LIKE
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a Like expression.
     */
    @NonNull
    public Expression like(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Like);
    }

    /**
     * Create a regex match expression that evaluates whether or not the current expression
     * regex matches the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return a regex match expression.
     */
    @NonNull
    public Expression regex(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.RegexLike);
    }

    /**
     * Create an IS expression that evaluates whether or not the current expression is equal to
     * the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS expression.
     */
    @NonNull
    public Expression is(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new BinaryExpression(this, expression, BinaryExpression.OpType.Is);
    }

    /**
     * Create an IS NOT expression that evaluates whether or not the current expression is not
     * equal to the given expression.
     *
     * @param expression the expression to compare with the current expression.
     * @return an IS NOT expression.
     */
    @NonNull
    public Expression isNot(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
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
    @NonNull
    public Expression between(@NonNull Expression expression1, @NonNull Expression expression2) {
        if (expression1 == null || expression2 == null) {
            throw new IllegalArgumentException("expression1 or expression2 cannot be null.");
        }
        final Expression aggr = new AggregateExpression(Arrays.asList(expression1, expression2));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.Between);
    }

    /**
     * Creates an IS NULL OR MISSING expression that evaluates whether or not the current
     * expression is null or missing.
     *
     * @return An IS NULL expression.
     */
    @NonNull
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
    @NonNull
    public Expression notNullOrMissing() {
        return negated(isNullOrMissing());
    }

    /**
     * Creates a Collate expression with the given Collation specification. Commonly
     * the collate expression is used in the Order BY clause or the string comparison
     * 　expression (e.g. equalTo or lessThan) to specify how the two strings are　compared.
     *
     * @param collation 　The collation object.
     * @return A Collate expression.
     */
    @NonNull
    public Expression collate(@NonNull Collation collation) {
        if (collation == null) {
            throw new IllegalArgumentException("collation cannot be null.");
        }
        return new CollationExpression(this, collation);
    }

    /**
     * Create an IN expression that evaluates whether or not the current expression is in the
     * given expressions.
     *
     * @param expressions the expression array to evaluate with.
     * @return an IN expression.
     */
    @NonNull
    public Expression in(@NonNull Expression... expressions) {
        if (expressions == null) {
            throw new IllegalArgumentException("expressions cannot be null.");
        }
        final Expression aggr = new AggregateExpression(Arrays.asList(expressions));
        return new BinaryExpression(this, aggr, BinaryExpression.OpType.In);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[json=%s]", getClass().getSimpleName(), asJSON());
    }

    abstract Object asJSON();
}
