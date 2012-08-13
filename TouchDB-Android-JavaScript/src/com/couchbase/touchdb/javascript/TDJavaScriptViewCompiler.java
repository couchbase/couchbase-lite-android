package com.couchbase.touchdb.javascript;

import java.util.List;
import java.util.Map;

import org.elasticsearch.script.javascript.support.NativeList;
import org.elasticsearch.script.javascript.support.NativeMap;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrapFactory;

import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDViewCompiler;
import com.couchbase.touchdb.TDViewMapBlock;
import com.couchbase.touchdb.TDViewMapEmitBlock;
import com.couchbase.touchdb.TDViewReduceBlock;

public class TDJavaScriptViewCompiler implements TDViewCompiler {

	@Override
	public TDViewMapBlock compileMapFunction(String mapSource, String language) {
        if (language.equals("javascript")) {
            return new TDViewMapBlockRhino(mapSource);
        }
        throw new IllegalArgumentException(language + " is not supported");
	}

	@Override
	public TDViewReduceBlock compileReduceFunction(String reduceSource, String language) {
        if (language.equals("javascript")) {
            return new TDViewReduceBlockRhino(reduceSource);
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

class TDViewMapBlockRhino implements TDViewMapBlock {

    private static WrapFactory wrapFactory = new CustomWrapFactory();
    private Scriptable globalScope;
    private String src;

    public TDViewMapBlockRhino(String src) {
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
    public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
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
            ctx.evaluateString(globalScope, mapSrc, "map", 1, null);

            //find the map function and execute it
            Function mapFun = (Function)globalScope.get("map", globalScope);
            Object[] functionArgs = { document };
            mapFun.call(ctx, globalScope, globalScope, functionArgs);

            //now pull values out of the place holder and emit them
            NativeArray mapResults = (NativeArray)globalScope.get("map_results", globalScope);
            for(int i=0; i<mapResults.getLength(); i++) {
                NativeArray mapResultItem = (NativeArray)mapResults.get(i);
                if(mapResultItem.getLength() == 2) {
                    Object key = mapResultItem.get(0);
                    Object value = mapResultItem.get(1);
                    emitter.emit(key, value);
                } else {
                    Log.e(TDDatabase.TAG, "Expected 2 element array with key and value");
                }

            }



        } finally {
            Context.exit();
        }

    }
    
}

class TDViewReduceBlockRhino implements TDViewReduceBlock {

    private static WrapFactory wrapFactory = new CustomWrapFactory();
    private Scriptable globalScope;
    private String src;

    public TDViewReduceBlockRhino(String src) {
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