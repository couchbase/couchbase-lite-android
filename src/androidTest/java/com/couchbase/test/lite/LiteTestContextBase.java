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
        // The assumption is that rootDirectory is just a container and all 'real' work will happen in a subdirectory.
        // Which means that clean up for a test should involve deleting the sub-directory, not the root directory since
        // individual tests can (and do) use multiple sub-directories. If we keep deleting the root directory then when
        // multiple contexts are created in different sub-directories they keep deleting each other!
        if (rootDirectory.exists() == false && rootDirectory.mkdir() == false ) {
            throw new RuntimeException("Couldn't create temporary directory for listener!");
        }
    }

    public File getRootDirectory() {
        return rootDirectory;
    }
}
