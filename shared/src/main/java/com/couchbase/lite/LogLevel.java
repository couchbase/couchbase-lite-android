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
 * Log level. The default log level for all domains is warning.
 * The log levels here are tentative and subject to change.
 */
public enum LogLevel {
    DEBUG(Log.C4LOG_DEBUG),
    VERBOSE(Log.C4LOG_VERBOSE),
    INFO(Log.C4LOG_INFO),
    WARNING(Log.C4LOG_WARN),
    ERROR(Log.C4LOG_ERROR),
    NONE(Log.C4LOG_NONE);

    private final int value;

    LogLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
