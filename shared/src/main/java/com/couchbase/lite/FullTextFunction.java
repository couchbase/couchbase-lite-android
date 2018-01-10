package com.couchbase.lite;


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
