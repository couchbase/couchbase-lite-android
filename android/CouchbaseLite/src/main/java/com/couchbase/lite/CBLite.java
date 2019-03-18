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


public final class CBLite {
    private static SoftReference<Context> context;

    private CBLite() {}

    public static void init(@NonNull Context ctxt) {
        CBLite.context = new SoftReference<>(ctxt.getApplicationContext());
    }

    @NonNull
    static Context getContext() {
        final Context ctxt = (context == null) ? null : context.get();
        if (ctxt == null) {
            throw new IllegalStateException("Null context.  Did you forget to call CBLite.init()?");
        }
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
