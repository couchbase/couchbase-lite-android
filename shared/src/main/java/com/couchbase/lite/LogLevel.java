//
// LogLevel.java
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
package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

/**
 * Log level.
 */
public enum LogLevel {
    /**
     * Debug log messages. Only present in debug builds of CouchbaseLite.
     */
    DEBUG(Log.C4LOG_DEBUG),

    /**
     * Verbose log messages.
     */
    VERBOSE(Log.C4LOG_VERBOSE),

    /**
     * Informational log messages.
     */
    INFO(Log.C4LOG_INFO),

    /**
     * Warning log messages.
     */
    WARNING(Log.C4LOG_WARN),

    /**
     * Error log messages. These indicate immediate errors that need to be addressed.
     */
    ERROR(Log.C4LOG_ERROR),

    /**
     * Disabling log messages of a given log domain.
     */
    NONE(Log.C4LOG_NONE);

    private final int value;

    LogLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
