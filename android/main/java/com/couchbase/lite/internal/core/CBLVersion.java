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
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.BuildConfig;

@SuppressWarnings({"PMD.ClassNamingConventions", "PMD.FieldNamingConventions"})
public final class CBLVersion {
    private CBLVersion() {}

    private static final String USER_AGENT = "CouchbaseLite/%s (%s) %s";
    private static final String VERSION_INFO = "CouchbaseLite Android v%s (%s at %s) on %s";
    private static final String LIB_INFO = "%s/%s, Commit/%s Core/%s";
    private static final String SYS_INFO = "Java; Android %s; %s";

    private static final AtomicReference<String> userAgent = new AtomicReference<>();
    private static final AtomicReference<String> versionInfo = new AtomicReference<>();
    private static final AtomicReference<String> libInfo = new AtomicReference<>();
    private static final AtomicReference<String> sysInfo = new AtomicReference<>();

    public static String getUserAgent() {
        String agent = userAgent.get();

        if (agent == null) {
            agent = String.format(
                Locale.ENGLISH,
                USER_AGENT,
                BuildConfig.VERSION_NAME,
                getSysInfo(),
                getLibInfo());

            userAgent.compareAndSet(null, agent);
        }

        return agent;
    }

    // This is information about this library build.
    public static String getVersionInfo() {
        String info = versionInfo.get();
        if (info == null) {
            info = String.format(
                Locale.ENGLISH,
                VERSION_INFO,
                BuildConfig.VERSION_NAME,
                getLibInfo(),
                BuildConfig.BUILD_TIME,
                getSysInfo());

            versionInfo.compareAndSet(null, info);
        }

        return info;
    }

    // This is information about this library build.
    public static String getLibInfo() {
        String info = libInfo.get();
        if (info == null) {
            info = String.format(
                Locale.ENGLISH,
                LIB_INFO,
                (BuildConfig.ENTERPRISE) ? "EE" : "CE",
                BuildConfig.BUILD_TYPE,
                BuildConfig.BUILD_COMMIT,
                C4.getVersion());

            libInfo.compareAndSet(null, info);
        }

        return info;
    }

    // This is information about the Android on which we are running.
    public static String getSysInfo() {
        String info = sysInfo.get();

        if (info == null) {
            final String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
            final String model = Build.MODEL;

            info = String.format(
                Locale.ENGLISH,
                SYS_INFO,
                (version.length() <= 0) ? "unknown" : version,
                (model.length() <= 0) ? "unknown" : model);

            sysInfo.compareAndSet(null, info);
        }

        return info;
    }
}
