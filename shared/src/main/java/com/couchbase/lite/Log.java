//
// Log.java
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

import com.couchbase.litecore.C4Log;

public final class Log {
    private final ConsoleLogger _consoleLogger = new ConsoleLogger();
    private final FileLogger _fileLogger = new FileLogger();
    private Logger _customLogger;

    Log() {
        C4Log.setCallbackLevel(LogLevel.WARNING.getValue());
    }

    public ConsoleLogger getConsole() {
        return _consoleLogger;
    }

    public FileLogger getFile() {
        return _fileLogger;
    }

    public Logger getCustom() {
        return _customLogger;
    }

    public void setCustom(Logger customLogger) {
        _customLogger = customLogger;
    }
}
