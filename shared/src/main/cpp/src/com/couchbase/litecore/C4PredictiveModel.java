package com.couchbase.litecore;

/**
 *  This is a simplified version of the C4PredictiveModel:
 *  1. No context object pass to the callback predict() method.
 *  2. The predict() method call not return the error back. The error will be logged from
 *     the Java code. If this result to confusion, we could make the predict method returns
 *     a complex object that includes a C4Error variable.
 */
public interface C4PredictiveModel {
    /*FLSliceResult*/ long predict(/*FLDict*/ long input, long c4db);
}

