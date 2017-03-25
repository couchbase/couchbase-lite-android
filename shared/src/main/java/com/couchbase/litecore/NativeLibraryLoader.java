package com.couchbase.litecore;

import com.couchbase.lite.Log;

public class NativeLibraryLoader {
    private static final String TAG = Log.DATABASE;

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";
    private static final String SHARED_SQLITE_LIBRARY = "sqlite3";

    public static void load() {
        boolean success = load(SHARED_SQLITE_LIBRARY);
        if (success)
            success = load(LITECORE_JNI_LIBRARY);
        if (success)
            Log.v(TAG, "Successfully load native library: 'LiteCoreJNI' and 'sqlite3'");
        else
            Log.e(TAG, "Cannot load native library");
    }

    private static boolean load(String libName) {
        // TODO: Need to update for CBL Java.
        return loadSystemLibrary(libName);
    }

    private static boolean loadSystemLibrary(String libName) {
        try {
            System.loadLibrary(libName);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
        return true;
    }
}
