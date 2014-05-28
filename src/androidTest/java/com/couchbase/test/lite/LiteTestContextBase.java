package com.couchbase.test.lite;

import com.couchbase.lite.support.*;

import java.io.*;

/**
 * Provides a platform specific way to create a safe temporary directory location since this is different in Java
 * and Android
 */
public class LiteTestContextBase {
    private File rootDirectory;

    public LiteTestContextBase() {
        rootDirectory = new File(System.getProperty("user.dir"), "data/data/com.couchbase.lite.test/files");
        FileDirUtils.deleteRecursive(rootDirectory);
        if (rootDirectory.mkdirs() == false) {
            throw new RuntimeException("Couldn't create temporary directory for listener!");
        }
    }

    public File getRootDirectory() {
        return rootDirectory;
    }
}
