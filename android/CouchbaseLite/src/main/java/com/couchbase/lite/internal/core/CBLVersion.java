//
// CBLVersion.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import android.os.Build;

import java.util.Locale;

import com.couchbase.lite.BuildConfig;


public class CBLVersion {
    private static volatile String userAgent;
    private static volatile String systemInfo;

    public static String getUserAgent() {
        if (userAgent == null) {
            final String liteCoreVers = C4.getVersion();
            userAgent = String.format(
                Locale.ENGLISH,
                "CouchbaseLite/%s %s Build/%d Commit/%.8s LiteCore/%s",
                BuildConfig.VERSION_NAME,
                getSystemInfo(),
                BuildConfig.BUILD_NO,
                BuildConfig.GitHash,
                liteCoreVers
            );
        }
        return userAgent;
    }

    public static String getSystemInfo() {
        if (systemInfo == null) {
            final StringBuilder result = new StringBuilder(64);
            result.append("(Java; Android ");
            final String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
            result.append(version.length() > 0 ? version : "1.0");
            // add the model for the release build
            if ("REL".equals(Build.VERSION.CODENAME)) {
                final String model = Build.MODEL;
                if (model.length() > 0) {
                    result.append("; ");
                    result.append(model);
                }
            }
            result.append(")");
            systemInfo = result.toString();
        }

        return systemInfo;
    }
}
