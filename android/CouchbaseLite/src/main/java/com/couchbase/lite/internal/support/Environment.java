package com.couchbase.lite.internal.support;

import com.couchbase.litecore.C4;

import java.io.File;

public final class Environment {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    public static synchronized void setupTempDirectory(File tempDir) {
        if (!tempDir.exists()) {
            if (tempDir.mkdirs()) {
                throw new IllegalStateException("Cannot create temporary directory at " +
                        tempDir.getAbsolutePath());
            }
        }
        C4.setenv("TMPDIR", tempDir.getAbsolutePath(), 1);
    }
}
