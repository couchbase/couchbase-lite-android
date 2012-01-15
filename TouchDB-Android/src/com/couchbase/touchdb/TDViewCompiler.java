package com.couchbase.touchdb;

public interface TDViewCompiler {

    TDViewMapBlock compileMapFunction(String mapSource, String language);

    TDViewReduceBlock compileReduceFunction(String reduceSource, String language);

}
