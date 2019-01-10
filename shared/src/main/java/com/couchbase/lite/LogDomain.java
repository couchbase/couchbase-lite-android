//
// LiveQuery.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import java.util.EnumSet;

/**
 * Log domain. The log domains here are tentative and subject to change.
 */
public enum LogDomain {

    /**
    * Gets all the logging interfaces so logic can be applied to
    * all of them
    **/
    ALL(1 << 1|1 << 2|1 << 3|1 << 4),

    /**
     * Gets the logging domain for database logging, which is responsible
     * for logging activity between the library and the disk, including creation
     * of Documents / Revisions, disk I/O, etc
     */
    DATABASE(1 << 1),

    /**
     * Gets the logging domain for query logging, which is responsible for
     * logging information about in progress queries on data.
     */
    QUERY(1 << 2),

    /**
     * Gets the logging domain for sync logging, which is responsible for
     * logging activity between the library and remote (network) endpoints.
     */
    REPLICATOR(1 << 3),

    /**
     * Gest the logging domain for network related logging (web socket connections,
     * BLIP protocol, etc)
     */
    NETWORK(1 << 4);

    private final int rawValue;

    private LogDomain(int rawValue) {
        this.rawValue = rawValue;
    }

    public int rawValue() {
        return rawValue;
    }
}
