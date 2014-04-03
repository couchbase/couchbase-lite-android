package com.couchbase.touchdb;

import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

public class RevCollator {
    public static void register(SQLiteDatabase database) {
        nativeRegister(database, Build.VERSION.SDK_INT);
    }

    private static native void nativeRegister(SQLiteDatabase database, int sdkVersion);

    // FIXME: only public for now until moved in to same package
    public static native int testCollateRevIds(String string1, String string2);

    static {
        System.loadLibrary("com_couchbase_touchdb_RevCollator");
    }
}
