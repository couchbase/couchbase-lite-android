package com.couchbase.lite.internal.core;

public final class C4Prediction {

    public static void register(String name, C4PredictiveModel model) {
        registerModel(name, model);
    }

    public static void unregister(String name) {
        unregisterModel(name);
    }

    static native void registerModel(String name, C4PredictiveModel model);

    static native void unregisterModel(String name);
}
