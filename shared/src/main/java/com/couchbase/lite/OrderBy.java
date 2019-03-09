//
// OrderBy.java
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
import java.util.List;


/**
 * An OrderBy represents an ORDER BY clause of the query for specifying properties or expressions
 * that the result rows should be sorted by.
 */
public final class OrderBy extends AbstractQuery implements LimitRouter {

    //---------------------------------------------
    // Member variables
    //---------------------------------------------

    private final List<Ordering> orderings;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    OrderBy(AbstractQuery query, List<Ordering> orderings) {
        copy(query);
        this.orderings = orderings;
        setOrderBy(this);
    }

    //---------------------------------------------
    // implementation of LimitRouter
    //---------------------------------------------

    /**
     * Creates and chains a Limit object to limit the number query results.
     *
     * @param limit The limit expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit) {
        if (limit == null) {
            throw new IllegalArgumentException("limit cannot be null.");
        }
        return new Limit(this, limit, null);
    }

    /**
     * Creates and chains a Limit object to skip the returned results for the given offset
     *
     * @param limit  The limit expression.
     * @param offset The offset expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit, Expression offset) {
        if (limit == null) {
            throw new IllegalArgumentException("limit cannot be null.");
        }
        return new Limit(this, limit, offset);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Object asJSON() {
        final List<Object> json = new ArrayList<>();
        for (Ordering ordering : orderings) { json.add(ordering.asJSON()); }
        return json;
    }
}
