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
import java.util.List;

/**
 * An OrderBy represents an ORDER BY clause of the query for specifying properties or expressions
 * that the result rows should be sorted by.
 */
public class OrderBy extends Query implements LimitRouter {

    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private List<Ordering> orderings;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    OrderBy(Query query, List<Ordering> orderings) {
        copy(query);
        this.orderings = orderings;
        setOrderBy(this);
    }

    //---------------------------------------------
    // implementation of LimitRouter
    //---------------------------------------------

    @Override
    public Limit limit(Object limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        throw new UnsupportedOperationException();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Object asJSON() {
        List<Object> json = new ArrayList<>();
        for (Ordering ordering : orderings)
            json.add(ordering.asJSON());
        return json;
    }
}
