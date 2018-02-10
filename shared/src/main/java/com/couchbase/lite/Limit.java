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
    private Expression limit;
    private Expression offset;

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
        List<Object> json = new ArrayList<>();
        json.add(limit.asJSON());
        if (offset != null)
            json.add(offset.asJSON());
        return json;
    }
}
