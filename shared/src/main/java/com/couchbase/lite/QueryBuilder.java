//
// QueryBuilder.java
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


public class QueryBuilder {
    /**
     * Create a SELECT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select object.
     */
    @NonNull
    public static Select select(@NonNull SelectResult... results) {
        if (results == null) { throw new IllegalArgumentException("results cannot be null."); }
        return new Select(false, results);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Create a SELECT DISTINCT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select distinct object.
     */
    @NonNull
    public static Select selectDistinct(@NonNull SelectResult... results) {
        if (results == null) { throw new IllegalArgumentException("results cannot be null."); }
        return new Select(true, results);
    }

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    private QueryBuilder() {
    }
}
