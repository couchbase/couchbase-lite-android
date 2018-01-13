package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

/**
 * A Limit component represents the LIMIT clause of the query statement.
 */
public class Limit extends Query {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Object limit;
    private Object offset;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Limit(Query query, Object limit, Object offset) {
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

        if (limit instanceof Expression)
            json.add(((Expression) limit).asJSON());
        else
            json.add(limit);

        if (offset != null) {
            if (offset instanceof Expression)
                json.add(((Expression) offset).asJSON());
            else
                json.add(offset);
        }

        return json;
    }
}
