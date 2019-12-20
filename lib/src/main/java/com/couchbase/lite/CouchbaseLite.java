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

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.fleece.MValue;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() {}

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt) { init(new MValueDelegate(), false, null, ctxt); }

    private static void init(
        @NonNull MValue.Delegate mValueDelegate,
        boolean debugging,
        @Nullable File rootDirectory,
        @NonNull Context ctxt) {
        CouchbaseLiteInternal.init(mValueDelegate, debugging, rootDirectory, ctxt);
    }
}
