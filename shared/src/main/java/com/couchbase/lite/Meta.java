package com.couchbase.lite;

import com.couchbase.lite.internal.query.expression.PropertyExpression;

public class Meta {
    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------
    public static class MetaExpression extends PropertyExpression {
        MetaExpression(String property) {
            super(property);
        }
    }

    //---------------------------------------------
    // API - public static variables
    //------------------------------------------
    public static final MetaExpression id = new MetaExpression("_id");
    public static final MetaExpression sequence = new MetaExpression("_sequence");
}
