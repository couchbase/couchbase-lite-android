//
// FullTextExpression.java
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

import java.util.Arrays;


/**
 * Full-text expression
 */
public final class FullTextExpression {
    static final class FullTextMatchExpression extends Expression {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private final String indexName;
        private final String text;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        FullTextMatchExpression(String indexName, String text) {
            this.indexName = indexName;
            this.text = text;
        }

        //---------------------------------------------
        // package level access
        //---------------------------------------------

        @Override
        Object asJSON() {
            return Arrays.asList("MATCH", indexName, text);
        }
    }

    /**
     * Creates a full-text expression with the given full-text index name.
     *
     * @param name The full-text index name.
     * @return The full-text expression.
     */
    @NonNull
    public static FullTextExpression index(@NonNull String name) {
        if (name == null) {
            throw new IllegalArgumentException("name is null.");
        }
        return new FullTextExpression(name);
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final String name;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextExpression(String name) {
        this.name = name;
    }

    /**
     * Creates a full-text match expression with the given search text.
     *
     * @param query The search text
     * @return The full-text match expression
     */
    @NonNull
    public Expression match(@NonNull String query) {
        if (query == null) {
            throw new IllegalArgumentException("query is null.");
        }
        return new FullTextMatchExpression(this.name, query);
    }
}
