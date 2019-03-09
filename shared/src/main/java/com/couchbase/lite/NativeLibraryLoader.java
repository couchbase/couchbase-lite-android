//
// NativeLibraryLoader.java
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
package com.couchbase.lite;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;


final class NativeLibraryLoader {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";

    private static final AtomicBoolean loaded = new AtomicBoolean(false);

    static void load() {
        if (!loaded.getAndSet(true)) {
            if (load(LITECORE_JNI_LIBRARY)) {
                Log.v(
                    DOMAIN,
                    "Successfully load native library: 'LiteCoreJNI' and 'sqlite3'");
            }
            else { Log.e(DOMAIN, "Cannot load native library"); }
            initMValue();
        }
    }

    private static boolean load(String libName) {
        // TODO: Need to update for CBL Java.
        return loadSystemLibrary(libName);
    }

    private static boolean loadSystemLibrary(String libName) {
        try {
            System.loadLibrary(libName);
        }
        catch (UnsatisfiedLinkError e) {
            return false;
        }
        return true;
    }

    private static void initMValue() {
        try {
            final Constructor c = Class.forName("com.couchbase.lite.MValueDelegate").getDeclaredConstructor();
            c.setAccessible(true);
            MValue.registerDelegate((MValue.Delegate) c.newInstance());
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot initialize MValue delegate", e);
        }
    }

    NativeLibraryLoader() { }
}
