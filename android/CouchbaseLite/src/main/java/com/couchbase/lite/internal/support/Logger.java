//
// Logger.java
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
package com.couchbase.lite.internal.support;

import android.util.Log;

final class Logger {
    void v(String tag, String msg) {
        Log.v(tag, msg);
    }

    void v(String tag, String msg, Throwable tr) {
        Log.v(tag, msg, tr);
    }

    void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    void i(String tag, String msg, Throwable tr) {
        Log.i(tag, msg, tr);
    }

    void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    void w(String tag, Throwable tr) {
        Log.w(tag, tr);
    }

    void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
    }

    void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }
}
