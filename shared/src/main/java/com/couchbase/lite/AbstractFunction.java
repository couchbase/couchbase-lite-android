//
// AbstractFunction.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import java.util.Arrays;


/**
 * Based class for Query Functions.
 */
abstract class AbstractFunction {
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
    @NonNull
    public static Expression avg(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("AVG()", Arrays.asList(expression));
    }

    /**
     * Creates a COUNT(expr) function expression that returns the count of all values
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The COUNT(expr) function.
     */
    @NonNull
    public static Expression count(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("COUNT()", Arrays.asList(expression));
    } // null expression -> count *

    /**
     * Creates a MIN(expr) function expression that returns the minimum value
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The MIN(expr) function.
     */
    @NonNull
    public static Expression min(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("MIN()", Arrays.asList(expression));
    }

    /**
     * Creates a MAX(expr) function expression that returns the maximum value
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The MAX(expr) function.
     */
    @NonNull
    public static Expression max(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("MAX()", Arrays.asList(expression));
    }

    /**
     * Creates a SUM(expr) function expression that return the sum of all number values
     * in the group of the values expressed by the given expression.
     *
     * @param expression The expression.
     * @return The SUM(expr) function.
     */
    @NonNull
    public static Expression sum(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("SUM()", Arrays.asList(expression));
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
    @NonNull
    public static Expression abs(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("ABS()", Arrays.asList(expression));
    }

    /**
     * Creates an ACOS(expr) function that returns the inverse cosine of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ACOS(expr) function.
     */
    @NonNull
    public static Expression acos(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("ACOS()", Arrays.asList(expression));
    }

    /**
     * Creates an ASIN(expr) function that returns the inverse sin of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ASIN(expr) function.
     */
    @NonNull
    public static Expression asin(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("ASIN()", Arrays.asList(expression));
    }

    /**
     * Creates an ATAN(expr) function that returns the inverse tangent of the numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ATAN(expr) function.
     */
    @NonNull
    public static Expression atan(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("ATAN()", Arrays.asList(expression));
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
    @NonNull
    public static Expression atan2(@NonNull Expression x, @NonNull Expression y) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("x and y cannot be null.");
        }
        return new Expression.FunctionExpression("ATAN2()", Arrays.asList(x, y));
    }

    /**
     * Creates a CEIL(expr) function that returns the ceiling value of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The CEIL(expr) function.
     */
    @NonNull
    public static Expression ceil(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("CEIL()", Arrays.asList(expression));
    }

    /**
     * Creates a COS(expr) function that returns the cosine of the given numeric expression.
     *
     * @param expression The expression.
     * @return The COS(expr) function.
     */
    @NonNull
    public static Expression cos(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("COS()", Arrays.asList(expression));
    }

    /**
     * Creates a DEGREES(expr) function that returns the degrees value of the given radiants
     * value expression.
     *
     * @param expression The expression.
     * @return The DEGREES(expr) function.
     */
    @NonNull
    public static Expression degrees(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("DEGREES()", Arrays.asList(expression));
    }

    /**
     * Creates a E() function that return the value of the mathemetical constant 'e'.
     *
     * @return The E() constant function.
     */
    @NonNull
    public static Expression e() {
        return new Expression.FunctionExpression("E()", Arrays.asList((Expression) null));
    }

    /**
     * Creates a EXP(expr) function that returns the value of 'e' power by the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The EXP(expr) function.
     */
    @NonNull
    public static Expression exp(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("EXP()", Arrays.asList(expression));
    }

    /**
     * Creates a FLOOR(expr) function that returns the floor value of the given
     * numeric expression.
     *
     * @param expression The expression.
     * @return The FLOOR(expr) function.
     */
    @NonNull
    public static Expression floor(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("FLOOR()", Arrays.asList(expression));
    }

    /**
     * Creates a LN(expr) function that returns the natural log of the given numeric expression.
     *
     * @param expression The expression.
     * @return The LN(expr) function.
     */
    @NonNull
    public static Expression ln(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("LN()", Arrays.asList(expression));
    }

    /**
     * Creates a LOG(expr) function that returns the base 10 log of the given numeric expression.
     *
     * @param expression The expression.
     * @return The LOG(expr) function.
     */
    @NonNull
    public static Expression log(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("LOG()", Arrays.asList(expression));
    }

    /**
     * Creates a PI() function that returns the mathemetical constant Pi.
     *
     * @return The PI() constant function.
     */
    @NonNull
    public static Expression pi() {
        return new Expression.FunctionExpression("PI()", Arrays.asList((Expression) null));
    }

    /**
     * Creates a POWER(base, exponent) function that returns the value of the given base
     * expression power the given exponent expression.
     *
     * @param base     The base expression.
     * @param exponent The exponent expression.
     * @return The POWER(base, exponent) function.
     */
    @NonNull
    public static Expression power(@NonNull Expression base, @NonNull Expression exponent) {
        if (base == null || exponent == null) {
            throw new IllegalArgumentException("base or exponent cannot be null.");
        }
        return new Expression.FunctionExpression("POWER()", Arrays.asList(base, exponent));
    }

    /**
     * Creates a RADIANS(expr) function that returns the radians value of the given degrees
     * value expression.
     *
     * @param expression The expression.
     * @return The RADIANS(expr) function.
     */
    @NonNull
    public static Expression radians(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("RADIANS()", Arrays.asList(expression));
    }

    /**
     * Creates a ROUND(expr) function that returns the rounded value of the given numeric
     * expression.
     *
     * @param expression The expression.
     * @return The ROUND(expr) function.
     */
    @NonNull
    public static Expression round(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("ROUND()", Arrays.asList(expression));
    }

    /**
     * Creates a ROUND(expr, digits) function that returns the rounded value to the given
     * number of digits of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @param digits     The number of digits.
     * @return The ROUND(expr, digits) function.
     */
    @NonNull
    public static Expression round(@NonNull Expression expression, @NonNull Expression digits) {
        if (expression == null || digits == null) {
            throw new IllegalArgumentException("expression and digits cannot be null.");
        }
        return new Expression.FunctionExpression("ROUND()", Arrays.asList(expression, digits));
    }

    /**
     * Creates a SIGN(expr) function that returns the sign (1: positive, -1: negative, 0: zero)
     * of the given numeric expression.
     *
     * @param expression The expression.
     * @return The SIGN(expr) function.
     */
    @NonNull
    public static Expression sign(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("SIGN()", Arrays.asList(expression));
    }

    /**
     * Creates a SIN(expr) function that returns the sin of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The SIN(expr) function.
     */
    @NonNull
    public static Expression sin(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("SIN()", Arrays.asList(expression));
    }

    /**
     * Creates a SQRT(expr) function that returns the square root of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The SQRT(expr) function.
     */
    @NonNull
    public static Expression sqrt(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("SQRT()", Arrays.asList(expression));
    }

    /**
     * Creates a TAN(expr) function that returns the tangent of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The TAN(expr) function.
     */
    @NonNull
    public static Expression tan(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("TAN()", Arrays.asList(expression));
    }

    /**
     * Creates a TRUNC(expr) function that truncates all of the digits after the decimal place
     * of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @return The trunc function.
     */
    @NonNull
    public static Expression trunc(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("TRUNC()", Arrays.asList(expression));
    }

    /**
     * Creates a TRUNC(expr, digits) function that truncates the number of the digits after
     * the decimal place of the given numeric expression.
     *
     * @param expression The numeric expression.
     * @param digits     The number of digits to truncate.
     * @return The TRUNC(expr, digits) function.
     */
    @NonNull
    public static Expression trunc(@NonNull Expression expression, @NonNull Expression digits) {
        if (expression == null || digits == null) {
            throw new IllegalArgumentException("expression and digits cannot be null.");
        }
        return new Expression.FunctionExpression("TRUNC()", Arrays.asList(expression, digits));
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
    @NonNull
    public static Expression contains(@NonNull Expression expression, @NonNull Expression substring) {
        if (expression == null || substring == null) {
            throw new IllegalArgumentException("expression and substring cannot be null.");
        }
        return new Expression.FunctionExpression("CONTAINS()", Arrays.asList(expression, substring));
    }

    /**
     * Creates a LENGTH(expr) function that returns the length of the given string expression.
     *
     * @param expression The string expression.
     * @return The LENGTH(expr) function.
     */
    @NonNull
    public static Expression length(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("LENGTH()", Arrays.asList(expression));
    }

    /**
     * Creates a LOWER(expr) function that returns the lowercase string of the given string
     * expression.
     *
     * @param expression The string expression.
     * @return The LOWER(expr) function.
     */
    @NonNull
    public static Expression lower(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("LOWER()", Arrays.asList(expression));
    }

    /**
     * Creates a LTRIM(expr) function that removes the whitespace from the beginning of the
     * given string expression.
     *
     * @param expression The string expression.
     * @return The LTRIM(expr) function.
     */
    @NonNull
    public static Expression ltrim(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("LTRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a RTRIM(expr) function that removes the whitespace from the end of the
     * given string expression.
     *
     * @param expression The string expression.
     * @return The RTRIM(expr) function.
     */
    @NonNull
    public static Expression rtrim(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("RTRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a TRIM(expr) function that removes the whitespace from the beginning and
     * the end of the given string expression.
     *
     * @param expression The string expression.
     * @return The TRIM(expr) function.
     */
    @NonNull
    public static Expression trim(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("TRIM()", Arrays.asList(expression));
    }

    /**
     * Creates a UPPER(expr) function that returns the uppercase string of the given string expression.
     *
     * @param expression The string expression.
     * @return The UPPER(expr) function.
     */
    @NonNull
    public static Expression upper(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("UPPER()", Arrays.asList(expression));
    }

    /**
     * Creates a MILLIS_TO_STR(expr) function that will convert a numeric input representing
     * milliseconds since the Unix epoch into a full ISO8601 date and time
     * string in the device local time zone.
     *
     * @param expression The string expression.
     * @return The MILLIS_TO_STR(expr) function.
     */
    @NonNull
    public static Expression millisToString(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("MILLIS_TO_STR()", Arrays.asList(expression));
    }

    /**
     * Creates a MILLIS_TO_UTC(expr) function that will convert a numeric input representing
     * milliseconds since the Unix epoch into a full ISO8601 date and time
     * string in UTC time.
     *
     * @param expression The string expression.
     * @return The MILLIS_TO_UTC(expr) function.
     */
    @NonNull
    public static Expression millisToUTC(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("MILLIS_TO_UTC()", Arrays.asList(expression));
    }

    /**
     * Creates a STR_TO_MILLIS(expr) that will convert an ISO8601 datetime string
     * into the number of milliseconds since the unix epoch.
     * Valid date strings must start with a date in the form YYYY-MM-DD (time
     * only strings are not supported).
     * <p>
     * Times can be of the form HH:MM, HH:MM:SS, or HH:MM:SS.FFF.  Leading zero is
     * not optional (i.e. 02 is ok, 2 is not).  Hours are in 24-hour format.  FFF
     * represents milliseconds, and *trailing* zeros are optional (i.e. 5 == 500).
     * <p>
     * Time zones can be in one of three forms:
     * (+/-)HH:MM
     * (+/-)HHMM
     * Z (which represents UTC)
     *
     * @param expression The string expression.
     * @return The STR_TO_MILLIS(expr) function.
     */
    @NonNull
    public static Expression stringToMillis(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("STR_TO_MILLIS()", Arrays.asList(expression));
    }

    /**
     * Creates a STR_TO_UTC(expr) that will convert an ISO8601 datetime string
     * into a full ISO8601 UTC datetime string.
     * Valid date strings must start with a date in the form YYYY-MM-DD (time
     * only strings are not supported).
     * <p>
     * Times can be of the form HH:MM, HH:MM:SS, or HH:MM:SS.FFF.  Leading zero is
     * not optional (i.e. 02 is ok, 2 is not).  Hours are in 24-hour format.  FFF
     * represents milliseconds, and *trailing* zeros are optional (i.e. 5 == 500).
     * <p>
     * Time zones can be in one of three forms:
     * (+/-)HH:MM
     * (+/-)HHMM
     * Z (which represents UTC)
     *
     * @param expression The string expression.
     * @return The STR_TO_UTC(expr) function.
     */
    @NonNull
    public static Expression stringToUTC(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Expression.FunctionExpression("STR_TO_UTC()", Arrays.asList(expression));
    }
}
