package com.couchbase.lite.utils;

import java.io.File;

public class FileUtils {

    public static boolean removeItemIfExists(String path) {
        File f = new File(path);
        return f.delete() || !f.exists();
    }

    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        return fileOrDirectory.delete() || !fileOrDirectory.exists();
    }

    public static boolean cleanDirectory(File dir) {
        if (!dir.isDirectory())
            return false;

        for (File file : dir.listFiles()) {
            if (!deleteRecursive(file))
                return false;
        }
        return true;
    }
}
