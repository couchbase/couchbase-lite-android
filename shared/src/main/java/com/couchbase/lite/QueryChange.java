//
// QueryChange.java
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


/**
 * QueryChange contains the information about the query result changes reported
 * by a query object.
 */
public final class QueryChange {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Query query;
    private final ResultSet rs;
    private final Throwable error;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    QueryChange(Query query, ResultSet rs, Throwable error) {
        this.query = query;
        this.rs = rs;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source live query object.
     */
    @NonNull
    public Query getQuery() {
        return query;
    }

    /**
     * Return the new query result.
     */
    @NonNull
    public ResultSet getResults() {
        return rs;
    }

    /**
     * Return the error occurred when running the query.
     */
    public Throwable getError() {
        return error;
    }
}
