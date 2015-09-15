package com.couchbase.lite.android;

public class SQLiteRevCollator {
    private static native void nativeRegister(Object database, String databaseClassName, int sdkVersion);

    public static void register(Object database, String databaseClassName, int sdkVersion) {
        nativeRegister(database, databaseClassName, sdkVersion);
    }

    public static native int testCollateRevIds(String string1, String string2);

    static {
        System.loadLibrary("SQLiteRevCollator");
    }
}
