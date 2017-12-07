package com.couchbase.lite.query;


import com.couchbase.lite.Expression;
import com.couchbase.lite.internal.query.expression.FunctionExpresson;

import java.util.Arrays;

public class FullTextFunction {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private FullTextFunction() {
    }

    //---------------------------------------------
    // FTS
    //---------------------------------------------
    public static Expression rank(String indexName) {
        return new FunctionExpresson("RANK()", Arrays.asList((Object) indexName));
    }
}
