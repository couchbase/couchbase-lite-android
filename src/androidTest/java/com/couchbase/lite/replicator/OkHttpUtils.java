/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;

import java.io.IOException;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.internal.Util;
import okio.Buffer;

/**
 * Created by hideki on 5/17/16.
 */
public class OkHttpUtils {
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final MediaType TEXT = MediaType.parse("plain/text");

    public static Map<String, Object> getJsonMapFromRequest(Request request) throws IOException {
        final Buffer buffer = new Buffer();
        try {
            final Request copy = request.newBuilder().build();
            copy.body().writeTo(buffer);
            return Manager.getObjectMapper().readValue(buffer.inputStream(), Map.class);
        } finally {
            Util.closeQuietly(buffer);
        }
    }
}
