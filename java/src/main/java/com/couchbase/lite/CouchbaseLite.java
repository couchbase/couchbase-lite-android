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

import android.support.annotation.NonNull;


public final class CouchbaseLite {
    private CouchbaseLite() {}

    public static void init() { }

    public static String getDbDirectoryPath() {
        return null;
    }

    public static String getTmpDirectory(@NonNull String name) {
        return null;
    }

    public static String getTmpDirectory(String root, String name) {
        return null;
    }
}
