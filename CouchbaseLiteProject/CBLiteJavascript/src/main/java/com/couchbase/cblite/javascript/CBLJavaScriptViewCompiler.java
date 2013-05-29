package com.couchbase.cblite.javascript;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.script.javascript.support.NativeList;
import org.elasticsearch.script.javascript.support.NativeMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLViewCompiler;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;
import com.couchbase.cblite.CBLViewReduceBlock;

public class CBLJavaScriptViewCompiler implements CBLViewCompiler {

	@Override
	public CBLViewMapBlock compileMapFunction(String mapSource, String language) {
        if (language.equals("javascript")) {
            return new CBLViewMapBlockRhino(mapSource);
        }
        throw new IllegalArgumentException(language + " is not supported");
	}

	@Override
	public CBLViewReduceBlock compileReduceFunction(String reduceSource, String language) {
        if (language.equals("javascript")) {
            return new CBLViewReduceBlockRhino(reduceSource);
        }
        throw new IllegalArgumentException(language + " is not supported");
	}

}

/**
 * Wrap Factory for Rhino Script Engine
 */
class CustomWrapFactory extends WrapFactory {

    public CustomWrapFactory() {
        setJavaPrimitiveWrap(false); // RingoJS does that..., claims its annoying...
    }

	@Override
    public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
        if (javaObject instanceof Map) {
            return new NativeMap(scope, (Map) javaObject);
        }
        else if(javaObject instanceof List) {
            return new NativeList(scope, (List<Object>)javaObject);
        }

        return super.wrapAsJavaObject(cx, scope, javaObject, staticType);
    }
}

// REFACT: Extract superview for both the map and reduce blocks as they do pretty much the same thing
class CBLViewMapBlockRhino implements CBLViewMapBlock {

    private static WrapFactory wrapFactory = new CustomWrapFactory();
    private Scriptable globalScope;
    private String src;

    public CBLViewMapBlockRhino(String src) {
        this.src = src;
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            ctx.setWrapFactory(wrapFactory);
            globalScope = ctx.initStandardObjects(null, true);
        } finally {
            Context.exit();
        }
    }

	@Override
    public void map(Map<String, Object> document, CBLViewMapEmitBlock emitter) {
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            ctx.setWrapFactory(wrapFactory);

            //create a place to hold results
            String placeHolder = "var map_results = [];";
            ctx.evaluateString(globalScope, placeHolder, "placeHolder", 1, null);

            //register the emit function
            String emitFunction = "var emit = function(key, value) { map_results.push([key, value]); };";
            ctx.evaluateString(globalScope, emitFunction, "emit", 1, null);

            //register the map function
            String mapSrc = "var map = " + src + ";";
            try {
            	ctx.evaluateString(globalScope, mapSrc, "map", 1, null);
            } catch(org.mozilla.javascript.EvaluatorException e) {
            	// Error in the JavaScript view - CouchDB swallows  the error and tries the next document
            	// REFACT: would be nice to check this in the constructor so we don't have to reparse every time
            	// should also be much faster if we can insert the map function into this objects globals
                Log.e(CBLDatabase.TAG, "Javascript syntax error in view:\n" + src, e);
                return;
            }
            
            // Need to stringify the json tree, as the ContextWrapper is unable
            // to correctly convert nested json to their js representation.
            // More specifically, if a dictionary is included that contains an array as a value 
            // that array will not be wrapped correctly but you'll get the plain 
            // java.util.ArrayList instead - and then an error.
            ObjectMapper mapper = new ObjectMapper();
            String json = null;
            try {
            	json = mapper.writeValueAsString(document);
			} catch (IOException e) {
				// Can thrown different subclasses of IOException- but we really do not care,
				// as this document was unserialized from JSON, so Jackson should be able to serialize it. 
				Log.e(CBLDatabase.TAG, "Error reserializing json from the db: " + document, e);
				return;
			}
            
            String mapInvocation = "map(" + json + ");";
            try {
            	ctx.evaluateString(globalScope, mapInvocation, "map invocation", 1, null);
            }
            catch (org.mozilla.javascript.RhinoException e) {
            	// Error in the JavaScript view - CouchDB swallows  the error and tries the next document
                Log.e(CBLDatabase.TAG, "Error in javascript view:\n" + src + "\n with document:\n" + document, e);
                return;
            }

            //now pull values out of the place holder and emit them
            NativeArray mapResults = (NativeArray)globalScope.get("map_results", globalScope);
            for(int i=0; i<mapResults.getLength(); i++) {
                NativeArray mapResultItem = (NativeArray)mapResults.get(i);
                if(mapResultItem.getLength() == 2) {
                    Object key = mapResultItem.get(0);
                    Object value = mapResultItem.get(1);
                    emitter.emit(key, value);
                } else {
                    Log.e(CBLDatabase.TAG, "Expected 2 element array with key and value");
                }

            }
        } finally {
            Context.exit();
        }

    }
    
}

class CBLViewReduceBlockRhino implements CBLViewReduceBlock {

    private static WrapFactory wrapFactory = new CustomWrapFactory();
    private Scriptable globalScope;
    private String src;

    public CBLViewReduceBlockRhino(String src) {
        this.src = src;
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            ctx.setWrapFactory(wrapFactory);
            globalScope = ctx.initStandardObjects(null, true);
        } finally {
            Context.exit();
        }
    }

	@Override
    public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
        Context ctx = Context.enter();
        try {
            ctx.setOptimizationLevel(-1);
            ctx.setWrapFactory(wrapFactory);

            //register the reduce function
            String reduceSrc = "var reduce = " + src + ";";
            ctx.evaluateString(globalScope, reduceSrc, "reduce", 1, null);

            //find the reduce function and execute it
            Function reduceFun = (Function)globalScope.get("reduce", globalScope);
            Object[] functionArgs = { keys, values, rereduce };
            Object result = reduceFun.call(ctx, globalScope, globalScope, functionArgs);

            return result;

        } finally {
            Context.exit();
        }

    }
    
}