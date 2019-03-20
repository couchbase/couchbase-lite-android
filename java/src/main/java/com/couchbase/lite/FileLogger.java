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
//
// FileLogger.java
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
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;


/**
 * A logger for writing to a file in the application's storage so
 * that log messages can persist durably after the application has
 * stopped or encountered a problem.  Each log level is written to
 * a separate file.
 */
public final class FileLogger implements Logger {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;
    private final HashMap<LogDomain, String> domainObjects = new HashMap<>();
    private LogLevel logLevel = LogLevel.INFO;
    private LogFileConfiguration config;

    // Singleton instance accessible from Log.getConsole()
    FileLogger() { setupDomainObjects(); }

    /**
     * Gets the configuration currently in use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @return The configuration currently in use
     */
    public LogFileConfiguration getConfig() {
        return config;
    }

    /**
     * Sets the configuration currently to use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @param config The configuration to use
     */
    public void setConfig(LogFileConfiguration config) {
        this.config = config == null ? null : config.readOnlyCopy();
        if (config == null) {
            Log.w(
                DOMAIN,
                "Database.log.getFile().getConfig() is now null, meaning file logging is disabled.  "
                    + "Log files required for product support are not being generated.");
        }

        updateConfig();
    }

    private void setupDomainObjects() {
        domainObjects.put(LogDomain.DATABASE, C4Constants.C4LogDomain.Database);
        domainObjects.put(LogDomain.QUERY, C4Constants.C4LogDomain.Query);
        domainObjects.put(LogDomain.REPLICATOR, C4Constants.C4LogDomain.Sync);
        for (Map.Entry<LogDomain, String> entry : domainObjects.entrySet()) {
            C4Log.setLevel(entry.getValue(), C4Constants.C4LogLevel.kC4LogDebug);
        }
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void updateConfig() {
        if (config != null) {
            new File(config.getDirectory()).mkdir();
            C4Log.writeToBinaryFile(
                config.getDirectory(), logLevel.getValue(), config.getMaxRotateCount(),
                config.getMaxSize(), config.usesPlaintext(), CBLVersion.getUserAgent());
        }
        else {
            C4Log.writeToBinaryFile(
                null,
                logLevel.getValue(),
                1,
                1024 * 500,
                false,
                CBLVersion.getUserAgent());
        }
    }

    @NonNull
    @Override
    public LogLevel getLevel() {
        return LogLevel.values()[C4Log.getBinaryFileLevel()];
    }

    /**
     * Sets the overall logging level that will be written to
     * the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if (config == null) {
            throw new IllegalStateException("Cannot set logging level without a configuration");
        }

        if (logLevel.equals(level)) {
            return;
        }

        logLevel = level;
        C4Log.setBinaryFileLevel(level.getValue());
    }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (level.compareTo(logLevel) < 0 || !domainObjects.containsKey(domain)) {
            return;
        }

        C4Log.log(domainObjects.get(domain), level.getValue(), message);
    }
}
