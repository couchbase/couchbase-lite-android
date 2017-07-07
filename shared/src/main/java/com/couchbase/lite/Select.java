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

import java.util.Arrays;

/**
 * Select represents the SELECT clause of the query for specifying the returning properties in each
 * query result row.
 */
public class Select extends Query implements FromRouter {
    /* package */ Select(boolean distinct, SelectResult... selectResults) {
        super();
        setDistinct(distinct);
        setSelectResults(Arrays.asList(selectResults));
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
}
