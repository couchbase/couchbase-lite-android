//
// Limit.java
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

import java.util.ArrayList;
import java.util.List;


/**
 * A Limit component represents the LIMIT clause of the query statement.
 */
public class Limit extends AbstractQuery {
    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final Expression limit;
    private final Expression offset;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    Limit(AbstractQuery query, Expression limit, Expression offset) {
        copy(query);
        this.limit = limit;
        this.offset = offset;
        setLimit(this);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Object asJSON() {
        final List<Object> json = new ArrayList<>();
        json.add(limit.asJSON());
        if (offset != null) { json.add(offset.asJSON()); }
        return json;
    }
}
