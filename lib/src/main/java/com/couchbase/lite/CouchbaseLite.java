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

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.ExecutionService;


public final class CouchbaseLite {
    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();
    private static final AtomicReference<SoftReference<Context>> CONTEXT = new AtomicReference<>();

    private CouchbaseLite() {}

    public static void init(@NonNull Context ctxt) {
        if (ctxt == null) { throw new IllegalArgumentException("Context may not be null"); }

        final SoftReference<Context> currentContext = CONTEXT.get();
        if (currentContext != null) { return; }

        CONTEXT.compareAndSet(null, new SoftReference<>(ctxt.getApplicationContext()));
    }

    public static ExecutionService getExecutionService() {
        ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService == null) {
            EXECUTION_SERVICE.compareAndSet(null, new AndroidExecutionService());
            executionService = EXECUTION_SERVICE.get();
        }
        return executionService;
    }

    @NonNull
    static Context getContext() {
        final SoftReference<Context> contextRef = CONTEXT.get();
        if (contextRef == null) {
            throw new IllegalStateException("Null context.  Did you forget to call CouchbaseLite.init()?");
        }

        final Context ctxt = contextRef.get();
        if (ctxt == null) { throw new IllegalStateException("Context is null"); }

        return ctxt;
    }

    static String getDbDirectoryPath() {
        return getContext().getFilesDir().getAbsolutePath();
    }

    static String getTmpDirectory(@NonNull String name) {
        return getTmpDirectory(getContext().getCacheDir().getAbsolutePath(), name);
    }

    static String getTmpDirectory(String root, String name) {
        final File dir = new File(root, name);

        final String path = dir.getAbsolutePath();
        if ((dir.exists() || dir.mkdirs()) && dir.isDirectory()) { return path; }

        throw new IllegalStateException("Cannot create or access temp directory at " + path);
    }
}
