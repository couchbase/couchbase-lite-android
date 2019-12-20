//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() {}

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt) { init(ctxt, null); }

    /**
     * Initialize CouchbaseLite library.
     * This method allows specifying a root directory for CBL files.
     * Use this version with great caution.
     *
     * @param rootDirectory the root directory for CBL files
     */
    public static void init(@NonNull Context ctxt, @Nullable File rootDirectory) {
        String rootDirPath = null;
        if (rootDirectory != null) {
            try { rootDirPath = rootDirectory.getCanonicalPath(); }
            catch (IOException e) {
                throw new IllegalArgumentException("Could not get path for directory: " + rootDirectory, e);
            }
        }

        CouchbaseLiteInternal.init(new MValueDelegate(), rootDirPath, ctxt);
    }
}
