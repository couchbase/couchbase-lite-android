//
// Function.java
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

import java.util.Arrays;

/**
 * Function provides query functions
 */
public final class Function {
    private Function() {

    }

    //---------------------------------------------
    // Aggregation
    //---------------------------------------------

    /**
     * Creates an AVG(expr) function expression that returns the average of all the number values
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The AVG(expr) function.
     */
    public static Expression avg(Expression expression) {
        return new Expression.FunctionExpresson("AVG()", Arrays.asList(expression));
    }

    /**
     * Creates a COUNT(expr) function expression that returns the count of all values
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The COUNT(expr) function.
     */
    public static Expression count(Expression expression) {
        return new Expression.FunctionExpresson("COUNT()", Arrays.asList(expression));
    } // null expression -> count *

    /**
     * Creates a MIN(expr) function expression that returns the minimum value
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The MIN(expr) function.
     */
    public static Expression min(Expression expression) {
        return new Expression.FunctionExpresson("MIN()", Arrays.asList(expression));
    }

    /**
     * Creates a MAX(expr) function expression that returns the maximum value
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The MAX(expr) function.
     */
    public static Expression max(Expression expression) {
        return new Expression.FunctionExpresson("MAX()", Arrays.asList(expression));
    }

    /**
     * Creates a SUM(expr) function expression that return the sum of all number values
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The SUM(expr) function.
     */
    public static Expression sum(Expression expression) {
        return new Expression.FunctionExpresson("SUM()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Math
    //---------------------------------------------

    /**
     * Creates an ABS(expr) function that returns the absolute value of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ABS(expr) function.
     */
    public static Expression abs(Expression expression) {
        return new Expression.FunctionExpresson("ABS()", Arrays.asList(expression));
    }

    /**
     * Creates an ACOS(expr) function that returns the inverse cosine of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ACOS(expr) function.
     */
    public static Expression acos(Expression expression) {
        return new Expression.FunctionExpresson("ACOS()", Arrays.asList(expression));
    }

    /**
     * Creates an ASIN(expr) function that returns the inverse sin of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ASIN(expr) function.
     */
    public static Expression asin(Expression expression) {
        return new Expression.FunctionExpresson("ASIN()", Arrays.asList(expression));
    }

    /**
     * Creates an ATAN(expr) function that returns the inverse tangent of the numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ATAN(expr) function.
     */
    public static Expression atan(Expression expression) {
        return new Expression.FunctionExpresson("ATAN()", Arrays.asList(expression));
    }

    /**
     * Returns the angle theta from the conversion of rectangular coordinates (x, y)
     * to polar coordinates (r, theta).
     *
     * @param x the abscissa coordinate
     * @param y the ordinate coordinate
     * @return the theta component of the point (r, theta) in polar coordinates that corresponds
     * to the point (x, y) in Cartesian coordinates.
     */
    public static Expression atan2(Expression x, Expression y) {
        return new Expression.FunctionExpresson("ATAN2()", Arrays.asList(x, y));
    }

    /**
     * Creates a CEIL(expr) function that returns the ceiling value of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The CEIL(expr) function.
     */
    public static Expression ceil(Expression expression) {
        return new Expression.FunctionExpresson("CEIL()", Arrays.asList(expression));
    }

    /**
     * Creates a COS(expr) function that returns the cosine of the given numeric expression.
     *
     * @param expression The expression.
     * @return The COS(expr) function.
     */
    public static Expression cos(Expression expression) {
        return new Expression.FunctionExpresson("COS()", Arrays.asList(expression));
    }

    /**
     * Creates a DEGREES(expr) function that returns the degrees value of the given radiants
     * value expression.
     *
     * @param expression The expression.
     * @return The DEGREES(expr) function.
     */
    public static Expression degrees(Expression expression) {
        return new Expression.FunctionExpresson("DEGREES()", Arrays.asList(expression));
    }

    /**
     * Creates a E() function that return the value of the mathemetical constant 'e'.
     *
     * @return The E() constant function.
     */
    public static Expression e() {
        return new Expression.FunctionExpresson("E()", Arrays.asList((Expression) null));
    }

    /**
     * Creates a EXP(expr) function that returns the value of 'e' power by the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The EXP(expr) function.
     */
    public static Expression exp(Expression expression) {
        return new Expression.FunctionExpresson("EXP()", Arrays.asList(expression));
    }

    /**
     * Creates a FLOOR(expr) function that returns the floor value of the given
     * numeric expression.
     *
     * @param expression The expression.
     * @return The FLOOR(expr) function.
     */
    public static Expression floor(Expression expression) {
        return new Expression.FunctionExpresson("FLOOR()", Arrays.asList(expression));
    }

    /**
     * Creates a LN(expr) function that returns the natural log of the given numeric expression.
     *
     * @param expression The expression.
     * @return The LN(expr) function.
     */
    public static Expression ln(Expression expression) {
        return new Expression.FunctionExpresson("LN()", Arrays.asList(expression));
    }

    /**
     * Creates a LOG(expr) function that returns the base 10 log of the given numeric expression.
     *
     * @param expression The expression.
     * @return The LOG(expr) function.
     */
    public static Expression log(Expression expression) {
        return new Expression.FunctionExpresson("LOG()", Arrays.asList(expression));
    }

    /**
     * Creates a PI() function that returns the mathemetical constant Pi.
     *
     * @return The PI() constant function.
     */
    public static Expression pi() {
        return new Expression.FunctionExpresson("PI()", Arrays.asList((Expression) null));
    }

    /**
     * Creates a POWER(base, exponent) function that returns the value of the given base
     * expression power the given exponent expression.
     *
     * @param base     The base expression.
     * @param exponent The exponent expression.
     * @return The POWER(base, exponent) function.
     */
    public static Expression power(Expression base, Expression exponent) {
        return new Expression.FunctionExpresson("POWER()", Arrays.asList(base, exponent));
    }

    /**
     * Creates a RADIANS(expr) function that returns the radians value of the given degrees
     * value expression.
     *
     * @param expression The expression.
     * @return The RADIANS(expr) function.
     */
    public static Expression radians(Expression expression) {
        return new Expression.FunctionExpresson("RADIANS()", Arrays.asList(expression));
    }

    /**
     * Creates a ROUND(expr) function that returns the rounded value of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ROUND(expr) function.
     */
    public static Expression round(Expression expression) {
        return new Expression.FunctionExpresson("ROUND()", Arrays.asList(expression));
    }

    /**
     * Creates a ROUND(expr, digits) function that returns the rounded value to the given
     * number of digits of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @param digits     The number of digits.
     * @return The ROUND(expr, digits) function.
     */
    public static Expression round(Expression expression, Expression digits) {
        return new Expression.FunctionExpresson("ROUND()", Arrays.asList(expression, digits));
    }

    /**
     * Creates a SIGN(expr) function that returns the sign (1: positive, -1: negative, 0: zero)
     * of the given numeric expression.
     *
     * @param expression The expression.
     * @return The SIGN(expr) function.
     */
    public static Expression sign(Expression expression) {
        return new Expression.FunctionExpresson("SIGN()", Arrays.asList(expression));
    }

    /**
     * Creates a SIN(expr) function that returns the sin of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The SIN(expr) function.
     */
    public static Expression sin(Expression expression) {
        return new Expression.FunctionExpresson("SIN()", Arrays.asList(expression));
    }

    /**
     * Creates a SQRT(expr) function that returns the square root of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The SQRT(expr) function.
     */
    public static Expression sqrt(Expression expression) {
        return new Expression.FunctionExpresson("SQRT()", Arrays.asList(expression));
    }

    /**
     * Creates a TAN(expr) function that returns the tangent of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The TAN(expr) function.
     */
    public static Expression tan(Expression expression) {
        return new Expression.FunctionExpresson("TAN()", Arrays.asList(expression));
    }

    /**
     * Creates a TRUNC(expr) function that truncates all of the digits after the decimal place
     * of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The trunc function.
     */
    public static Expression trunc(Expression expression) {
        return new Expression.FunctionExpresson("TRUNC()", Arrays.asList(expression));
    }

    /**
     * Creates a TRUNC(expr, digits) function that truncates the number of the digits after
     * the decimal place of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @param digits     The number of digits to truncate.
     * @return The TRUNC(expr, digits) function.
     */
    public static Expression trunc(Expression expression, Expression digits) {
        return new Expression.FunctionExpresson("TRUNC()", Arrays.asList(expression, digits));
    }

    //---------------------------------------------
    // String
    //---------------------------------------------

    /**
     * Creates a CONTAINS(expr, substr) function that evaluates whether the given string
     * expression conatins the given substring expression or not.
     *
     * @param expression The string expression.
     * @param substring  The substring expression.
     * @return The CONTAINS(expr, substr) function.
     */
    public static Expression contains(Expression expression, Expression substring) {
        return new Expression.FunctionExpresson("CONTAINS()", Arrays.asList(expression, substring));
    }

    /**
     * Creates a LENGTH(expr) function that returns the length of the given string expression.
     *
     * @param expression The string expression.
     * @return The LENGTH(expr) function.
     */
    public static Expression length(Expression expression) {
        return new Expression.FunctionExpresson("LENGTH()", Arrays.asList(expression));
    }

    /**
     * Creates a LOWER(expr) function that returns the lowercase string of the given string
     * expression.
     *
     * @param expression The string expression.
     * @return The LOWER(expr) function.
     */
    public static Expression lower(Expression expression) {
        return new Expression.FunctionExpresson("LOWER()", Arrays.asList(expression));
    }

    /**
     * Creates a LTRIM(expr) function that removes the whitespace from the beginning of the
     * given string expression.
     *
     * @param expression The string expression.
     * @return The LTRIM(expr) function.
     */
    public static Expression ltrim(Expression expression) {
        return new Expression.FunctionExpresson("LTRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a RTRIM(expr) function that removes the whitespace from the end of the
     * given string expression.
     *
     * @param expression The string expression.
     * @return The RTRIM(expr) function.
     */
    public static Expression rtrim(Expression expression) {
        return new Expression.FunctionExpresson("RTRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a TRIM(expr) function that removes the whitespace from the beginning and
     * the end of the given string expression.
     *
     * @param expression The string expression.
     * @return The TRIM(expr) function.
     */
    public static Expression trim(Expression expression) {
        return new Expression.FunctionExpresson("TRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a UPPER(expr) function that returns the uppercase string of the given string expression.
     *
     * @param expression The string expression.
     * @return The UPPER(expr) function.
     */
    public static Expression upper(Expression expression) {
        return new Expression.FunctionExpresson("UPPER()", Arrays.asList(expression));
    }
}
