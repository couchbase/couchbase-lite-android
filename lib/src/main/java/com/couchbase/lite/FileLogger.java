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
import android.support.annotation.Nullable;

import java.io.File;

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
    private LogFileConfiguration config;
    private LogLevel logLevel = LogLevel.WARNING;

    // The singleton instance is available from Database.log.getFile()
    FileLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (level.compareTo(logLevel) < 0) { return; }
        C4Log.log(Log.getC4DomainForLoggingDomain(domain), level.getValue(), message);
    }

    @NonNull
    @Override
    public LogLevel getLevel() {
        logLevel = Log.getLogLevelForC4Level(C4Log.getBinaryFileLevel());
        return logLevel;
    }

    /**
     * Sets the overall logging level that will be written to the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if (config == null) {
            throw new IllegalStateException("Cannot set logging level before setting the configuration");
        }

        if (logLevel == level) { return; }

        logLevel = level;
        C4Log.setBinaryFileLevel(level.getValue());
    }

    /**
     * Gets the configuration currently in use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @return The configuration currently in use
     */
    public LogFileConfiguration getConfig() { return config; }

    /**
     * Sets the configuration currently to use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @param config The configuration to use
     */
    public void setConfig(@Nullable LogFileConfiguration config) {
        if (config == null) {
            setNullConfig();
            return;
        }

        this.config = config.readOnlyCopy();

        if (!new File(config.getDirectory()).mkdir()) {
            Log.w(LogDomain.DATABASE, "Cannot create log file!");
        }

        C4Log.writeToBinaryFile(
            config.getDirectory(),
            LogLevel.INFO.getValue(),
            config.getMaxRotateCount(),
            config.getMaxSize(),
            config.usesPlaintext(),
            CBLVersion.getVersionInfo());
    }

    private void setNullConfig() {
        Log.w(
            LogDomain.DATABASE,
            "Database.log.getFile().getConfig() is now null and file logging is disabled.  "
                + "The log files *required* for product support are not being generated.");

        this.config = null;

        C4Log.writeToBinaryFile(
            null,
            LogLevel.INFO.getValue(),
            1,
            1024 * 500,
            false,
            CBLVersion.getVersionInfo());
    }
}
