//
// Collation.java
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

import java.util.HashMap;
import java.util.Map;


/**
 * Collation defines how strings are compared and is used when creating a COLLATE expression.
 * The COLLATE expression can be used in the WHERE clause when comparing two strings or in the
 * ORDER BY clause when specifying how the order of the query results. CouchbaseLite provides
 * two types of the Collation, ASCII and Unicode. Without specifying the COLLATE expression
 * Couchbase Lite will use the ASCII with case sensitive collation by default.
 */
public class Collation {

    /**
     * ASCII collation compares two strings by using binary comparison.
     */
    public static final class ASCII extends Collation {
        ASCII() {
            this.isUnicode = false;
        }

        /**
         * Specifies whether the collation is case-sensitive or not. Case-insensitive
         * collation will treat ASCII uppercase and lowercase letters as equivalent.
         *
         * @param ignoreCase True for case-insenstivie; false for case-senstive.
         * @return The ASCII Collation object.
         */
        @NonNull
        public ASCII ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }
    }

    /**
     * <a href="http://userguide.icu-project.org/collation">Unicode Collation</a> that will compare two strings
     * by using Unicode collation algorithm. If the locale is not specified, the collation is
     * Unicode-aware but not localized; for example, accented Roman letters sort right after
     * the base letter
     */
    public static final class Unicode extends Collation {
        Unicode() {
            this.isUnicode = true;
            // NOTE: System.getProperty("user.country") returns null for country code
            this.locale = System.getProperty("user.language");
        }

        /**
         * Specifies whether the collation is case-insenstive or not. Case-insensitive
         * collation will treat ASCII uppercase and lowercase letters as equivalent.
         *
         * @param ignoreCase True for case-insenstivie; false for case-senstive.
         * @return The Unicode Collation object.
         */
        @NonNull
        public Unicode ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }

        /**
         * Specifies whether the collation ignore the accents or diacritics when
         * comparing the strings or not.
         *
         * @param ignoreAccents True for accent-insenstivie; false for accent-senstive.
         * @return The Unicode Collation object.
         */
        @NonNull
        public Unicode ignoreAccents(boolean ignoreAccents) {
            this.ignoreAccents = ignoreAccents;
            return this;
        }

        /**
         * Specifies the locale to allow the collation to compare strings appropriately base on
         * the locale.
         *
         * @param locale The locale code which is an [ISO-639](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes)
         *               language code plus, optionally, an underscore and an
         *               [ISO-3166](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)
         *               country code: "en", "en_US", "fr_CA", etc.
         *               Specifing the locale will allow the collation to compare strings
         *               appropriately base on the locale.
         * @return
         */
        @NonNull
        public Unicode locale(String locale) {
            this.locale = locale;
            return this;
        }
    }

    /**
     * Creates an ASCII collation that will compare two strings by using binary comparison.
     *
     * @return The ASCII collation.
     */
    @NonNull
    public static ASCII ascii() {
        return new ASCII();
    }

    /**
     * Creates a Unicode collation that will compare two strings by using Unicode Collation
     * Algorithm. If the locale is not specified, the collation is Unicode-aware but
     * not localized; for example, accented Roman letters sort right after the base letter
     *
     * @return The Unicode collation.
     */
    @NonNull
    public static Unicode unicode() {
        return new Unicode();
    }

    boolean isUnicode;
    boolean ignoreCase;
    boolean ignoreAccents;
    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    String locale;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private Collation() {
    }

    @NonNull
    @Override
    public String toString() {
        return "Collation{" +
            "isUnicode=" + isUnicode +
            ", ignoreCase=" + ignoreCase +
            ", ignoreAccents=" + ignoreAccents +
            ", locale='" + locale + '\'' +
            '}';
    }

    Object asJSON() {
        final Map<String, Object> json = new HashMap<>();
        json.put("UNICODE", isUnicode);
        json.put("LOCALE", locale);
        json.put("CASE", !ignoreCase);
        json.put("DIAC", !ignoreAccents);
        return json;
    }
}
