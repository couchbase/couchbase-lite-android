//
// DefaultExecutor.java
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

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;


final class DefaultExecutor implements Executor {

    private static volatile DefaultExecutor instance;

    public static DefaultExecutor getInstance() {
        if (instance == null) { instance = new DefaultExecutor(); }
        return instance;
    }
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DefaultExecutor() {
    }

    @Override
    public void execute(Runnable runnable) {
        handler.post(runnable);
    }
}
