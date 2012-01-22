package com.couchbase.touchdb;

/**
 * An external object that knows how to map source code of some sort into executable functions.
 */
public interface TDViewCompiler {

    TDViewMapBlock compileMapFunction(String mapSource, String language);

    TDViewReduceBlock compileReduceFunction(String reduceSource, String language);

}
