package com.couchbase.cblite;

/**
 * An external object that knows how to map source code of some sort into executable functions.
 */
public interface CBLViewCompiler {

    CBLViewMapBlock compileMapFunction(String mapSource, String language);

    CBLViewReduceBlock compileReduceFunction(String reduceSource, String language);

}
