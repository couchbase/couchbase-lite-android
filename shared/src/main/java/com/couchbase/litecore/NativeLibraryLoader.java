/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.litecore;

import com.couchbase.lite.Log;

public class NativeLibraryLoader {
    private static final String TAG = Log.DATABASE;

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";
    
    public static void load() {
        if (load(LITECORE_JNI_LIBRARY))
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
