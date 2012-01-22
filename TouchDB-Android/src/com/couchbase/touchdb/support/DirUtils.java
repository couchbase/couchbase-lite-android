/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.touchdb.support;

import java.io.File;

import android.util.Log;

import com.couchbase.touchdb.TDDatabase;

public class DirUtils {

    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                return deleteRecursive(child);

        return fileOrDirectory.delete();
    }

    public static String getDatabaseNameFromPath(String path) {
        int lastSlashPos = path.lastIndexOf("/");
        int extensionPos = path.lastIndexOf(".");
        if(lastSlashPos < 0 || extensionPos < 0 || extensionPos < lastSlashPos) {
            Log.e(TDDatabase.TAG, "Unable to determine database name from path");
            return null;
        }
        return path.substring(lastSlashPos + 1, extensionPos);
    }

}
