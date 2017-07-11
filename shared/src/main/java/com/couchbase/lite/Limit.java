package com.couchbase.lite;

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
    boolean hasOffset() {
        return offset != null;
    }

    Object asJSON() {
        if (limit instanceof Expression)
            return ((Expression) limit).asJSON();
        else
            return limit;
    }

    Object asJSONOffset() {
        if (offset instanceof Expression)
            return ((Expression) offset).asJSON();
        else
            return offset;
    }
}
