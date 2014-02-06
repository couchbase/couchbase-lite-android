/**
 * Created by Wayne Carter.
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite.android;

import android.util.Log;

import com.couchbase.lite.util.Logger;

public class AndroidLogger implements Logger {
    @Override
    public void v(String tag, String msg) {
        Log.v(tag, msg);
    }

    @Override
    public void v(String tag, String msg, Throwable tr) {
        Log.v(tag, msg, tr);
    }

    @Override
    public void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    @Override
    public void d(String tag, String msg, Throwable tr) {
        Log.d(tag, msg, tr);
    }

    @Override
    public void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    @Override
    public void i(String tag, String msg, Throwable tr) {
        Log.i(tag, msg, tr);
    }

    @Override
    public void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    @Override
    public void w(String tag, Throwable tr) {
        Log.w(tag, tr);
    }

    @Override
    public void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
    }

    @Override
    public void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    @Override
    public void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
    }
}
