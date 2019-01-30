//
// FileUtils.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.litecore.utils;

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
        dir.delete();
        return true;
    }
}
