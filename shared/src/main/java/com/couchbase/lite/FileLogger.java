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

public final class FileLogger implements Logger {
    private LogLevel _level = LogLevel.INFO;
    private String _directory;
    private boolean _customDirectory;
    private boolean _hasConfigChanges;
    private int _maxRotateCount = 1;
    private long _maxSize = 1024;
    private boolean _usePlaintext;
    private final HashMap<LogDomain, String> _domainObjects = new HashMap<>();
    private static String DefaultDirectory;

    FileLogger() {
        setupDomainObjects();
    }

    public int getMaxRotateCount() {
        return _maxRotateCount;
    }

    public void setMaxRotateCount(int maxRotateCount) {
        if(_maxRotateCount == maxRotateCount) {
            return;
        }

        _maxRotateCount = maxRotateCount;
        _hasConfigChanges = true;
    }

    public long getMaxSize() {
        return _maxSize;
    }

    public void setMaxSize(long maxSize) {
        if(_maxSize == maxSize) {
            return;
        }

        _maxSize = maxSize;
        _hasConfigChanges = true;
    }

    public boolean getUsePlaintext() {
        return _usePlaintext;
    }

    public void setUsePlaintext(boolean usePlaintext) {
        if(_usePlaintext == usePlaintext) {
            return;
        }

        _usePlaintext = usePlaintext;
        _hasConfigChanges = true;
    }


    public String getDirectory() {
        return _directory;
    }

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