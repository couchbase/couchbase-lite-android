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
    private String _directory;
    private boolean _customDirectory;
    private boolean _hasConfigChanges;
    private int _maxRotateCount = 1;
    private long _maxSize = 500 * 1024;
    private boolean _usePlaintext;
    private final HashMap<LogDomain, String> _domainObjects = new HashMap<>();
    private static String DefaultDirectory;

    //---------------------------------------------
    // Constructor should not be exposed (singleton)
    //---------------------------------------------
    FileLogger() {
        setupDomainObjects();
    }

    /**
     * Gets the maximum number of rotated logs to keep on
     * the disk
     *
     * @return The number of *rotated* logs to keep (The total
     * number of disk entries will be this number plus 1 for
     * current log)
     */
    public int getMaxRotateCount() {
        return _maxRotateCount;
    }

    /**
     * Sets the maximum number of rotated logs to keep on
     * the disk
     *
     * @param maxRotateCount The number of *rotated* logs to keep
     *                       (The total number of disk entries will
     *                       be this number plus 1 for current log)
     */
    public void setMaxRotateCount(int maxRotateCount) {
        if(_maxRotateCount == maxRotateCount) {
            return;
        }

        _maxRotateCount = maxRotateCount;
        _hasConfigChanges = true;
    }

    /**
     * Gets the maximum size that a log file can grow to before
     * performing a rollover.
     *
     * @return The maximum size of a given log file
     */
    public long getMaxSize() {
        return _maxSize;
    }

    /**
     * Sets the maximum size that a log file can grow to before
     * performing a rollover.
     *
     * @param maxSize The maximum size of a given log file
     */
    public void setMaxSize(long maxSize) {
        if(_maxSize == maxSize) {
            return;
        }

        _maxSize = maxSize;
        _hasConfigChanges = true;
    }

    /**
     * Gets whether or not the logger will write to the file
     * in plaintext (default is a proprietary binary encoding)
     *
     * @return Whether or not the logger uses plaintext
     */
    public boolean getUsePlaintext() {
        return _usePlaintext;
    }

    /**
     * Sets whether or not the logger will write to the file
     * in plaintext (default is a proprietary binary encoding).
     * Plaintext logging is not recommended because of performance
     * hits.
     *
     * @param usePlaintext Whether or not the logger uses plaintext
     */
    public void setUsePlaintext(boolean usePlaintext) {
        if(_usePlaintext == usePlaintext) {
            return;
        }

        _usePlaintext = usePlaintext;
        _hasConfigChanges = true;
    }

    /**
     * Gets the directory that the logger will write to when
     * writing its logs.
     *
     * @return The directory that the logger is writing to
     */
    public String getDirectory() {
        return _directory;
    }

    /**
     * Sets the directory that the logger will write to when
     * writing its logs.  It is recommended to do this as
     * early as possible because the library has no information
     * about "default" locations until the first database
     * configuration object is created
     *
     * @param directory The directory that the logger will write to
     */
    public void setDirectory(String directory) {
        if(directory == null) {
            directory = DefaultDirectory;
        }

        File directoryObj = new File(directory);
        directoryObj.mkdirs();

        _directory = directory;
        _customDirectory = true;
        updateConfig();
    }

    /**
     * Sets the overall logging level that will be written to
     * the logging files.
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(LogLevel level) {
        if(level == null) {
            level = LogLevel.NONE;
        }

        if(_level.equals(level)) {
            return;
        }

        _level = level;
        C4Log.setBinaryFileLevel(level.getValue());
    }

    void initializeDefaultDirectory(String directory) {
        String oldDir = _directory;
        if(DefaultDirectory != directory) {
            DefaultDirectory = directory;
        }

        if(!_customDirectory) {
            File directoryObj = new File(DefaultDirectory);
            directoryObj.mkdirs();

            // Don't use public setter, it will register as custom
            _directory = directory;
            updateConfig();
        }
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
        C4Log.writeToBinaryFile(_directory, _level.getValue(), _maxRotateCount, _maxSize, _usePlaintext);
        _hasConfigChanges = false;
    }

    @Override
    public LogLevel getLevel() {
        return LogLevel.values()[C4Log.getBinaryFileLevel()];
    }

    @Override
    public void log(LogLevel level, LogDomain domain, String message) {
        if(_hasConfigChanges) {
            updateConfig();
        }

        if(level.compareTo(_level) < 0 || !_domainObjects.containsKey(domain)) {
            return;
        }

        C4Log.log(_domainObjects.get(domain), level.getValue(), message);
    }
}