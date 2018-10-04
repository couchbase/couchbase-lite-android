package com.couchbase.lite.internal.support;

import android.content.Context;
import com.couchbase.litecore.C4;

public final class Assets {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    public static synchronized void initialize(Context context) {
        String tempDirectory = context.getCacheDir().getAbsolutePath();
        C4.setenv("TMPDIR", tempDirectory, 1);
    }
}
