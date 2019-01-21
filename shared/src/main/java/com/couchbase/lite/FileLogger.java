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

import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A logger for writing to a file in the application's storage so
 * that log messages can persist durably after the application has
 * stopped or encountered a problem.  Each log level is written to
 * a separate file.
 */
public final class FileLogger implements Logger {
    private LogLevel _level = LogLevel.INFO;
    private LogFileConfiguration _config;
    private final HashMap<LogDomain, String> _domainObjects = new HashMap<>();

    //---------------------------------------------
    // Constructor should not be exposed (singleton)
    //---------------------------------------------
    FileLogger() {
        setupDomainObjects();
    }

    /**
     * Gets the configuration currently in use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @return The configuration currently in use
     */
    public LogFileConfiguration getConfig() {
        return _config;
    }

    /**
     * Sets the configuration currently to use on the file logger.
     * Note that once it is set, it can no longer be modified and doing so
     * will throw an exception
     *
     * @param config The configuration to use
     */
    public void setConfig(LogFileConfiguration config) {
        _config = config == null ? null : config.readOnlyCopy();
        updateConfig();
    }

    /**
     * Sets the overall logging level that will be written to
     * the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(LogLevel level) {
        if(_config == null) {
            throw new IllegalStateException("Cannot set logging level without a configuration");
        }

        if(level == null) {
            level = LogLevel.NONE;
        }

        if(_level.equals(level)) {
            return;
        }

        _level = level;
        C4Log.setBinaryFileLevel(level.getValue());
    }

    private void setupDomainObjects() {
        _domainObjects.put(LogDomain.DATABASE, C4Constants.C4LogDomain.Database);
        _domainObjects.put(LogDomain.QUERY, C4Constants.C4LogDomain.Query);
        _domainObjects.put(LogDomain.REPLICATOR, C4Constants.C4LogDomain.Sync);
        for (Map.Entry<LogDomain, String> entry : _domainObjects.entrySet()) {
            C4Log.setLevel(entry.getValue(), C4Constants.C4LogLevel.kC4LogDebug);
        }
    }

    private void updateConfig() {
        if(_config != null) {
            new File(_config.getDirectory()).mkdir();
            C4Log.writeToBinaryFile(_config.getDirectory(), _level.getValue(), _config.getMaxRotateCount(),
                    _config.getMaxSize(), _config.usesPlaintext());
        } else {
            C4Log.writeToBinaryFile(null, _level.getValue(), 1, 1024 * 500, false);
        }
    }

    @Override
    public LogLevel getLevel() {
        return LogLevel.values()[C4Log.getBinaryFileLevel()];
    }

    @Override
    public void log(LogLevel level, LogDomain domain, String message) {
        if(level.compareTo(_level) < 0 || !_domainObjects.containsKey(domain)) {
            return;
        }

        C4Log.log(_domainObjects.get(domain), level.getValue(), message);
    }
}