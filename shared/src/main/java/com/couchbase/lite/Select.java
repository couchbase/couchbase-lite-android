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
 * Select represents the SELECT clause of the query for specifying the returning properties in each
 * query result row.
 */
public class Select extends Query implements FromRouter {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private boolean distinct;                 // DISTINCT
    private List<SelectResult> selectResults; // result-columns

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    Select(boolean distinct, SelectResult... selectResults) {
        this.distinct = distinct;
        this.selectResults = Arrays.asList(selectResults);
        setSelect(this);
    }

    //---------------------------------------------
    // implementation of FromRouter
    //---------------------------------------------

    /**
     * Create and chain a FROM component for specifying the data source of the query.
     *
     * @param dataSource the data source.
     * @return the From component.
     */
    @Override
    public From from(DataSource dataSource) {
        return new From(this, dataSource);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    boolean isDistinct() {
        return distinct;
    }

    boolean hasSelectResults() {
        return selectResults.size() > 0;
    }

    Object asJSON() {
        List<Object> json = new ArrayList<Object>();
        for (SelectResult sr : selectResults)
            json.add(sr.asJSON());
        return json;
    }
}
