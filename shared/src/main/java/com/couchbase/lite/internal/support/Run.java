//
// RunOnce.java
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
package com.couchbase.lite.internal.support;

import android.support.annotation.NonNull;

import java.util.HashSet;

 public final class Run {
    private static final HashSet<String> Instances = new HashSet<>();

    @NonNull
    public static synchronized void once(@NonNull String tag, @NonNull Runnable action) {
        if(tag == null) {
            throw new IllegalArgumentException("tag cannot be null");
        }

        if(action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }

        if(Instances.contains(tag)) {
            return;
        }

        Instances.add(tag);
        action.run();
    }
}
