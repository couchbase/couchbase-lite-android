//
// C4Log.java
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
package com.couchbase.litecore;

import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;

import java.util.HashMap;
import java.util.concurrent.Executors;

public class C4Log {
    private static LogLevel CurrentLevel = LogLevel.WARNING;
    private static HashMap<String, LogDomain> DomainObjects = new HashMap<>();

    static {
        DomainObjects.put(C4Constants.C4LogDomain.Database, LogDomain.DATABASE);
        DomainObjects.put(C4Constants.C4LogDomain.Query, LogDomain.QUERY);
        DomainObjects.put(C4Constants.C4LogDomain.Sync, LogDomain.REPLICATOR);
        DomainObjects.put(C4Constants.C4LogDomain.SyncBusy, LogDomain.REPLICATOR);
        DomainObjects.put(C4Constants.C4LogDomain.BLIP, LogDomain.NETWORK);
        DomainObjects.put(C4Constants.C4LogDomain.WebSocket, LogDomain.NETWORK);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public static native void setLevel(String domain, int level);

    public static native void log(String domain, int level, String message);

    public static native int getBinaryFileLevel();

    public static native void setBinaryFileLevel(int level);

    public static native void writeToBinaryFile(String path, int level, int maxRotateCount, long maxSize, boolean usePlaintext, String header);

    public static native void setCallbackLevel(int level);

    static void logCallback(String domainName, int level, String message) {
        recalculateLevels();

        LogDomain domain = LogDomain.DATABASE;
        if(DomainObjects.containsKey(domainName)) {
            domain = DomainObjects.get(domainName);
        }

        Database.log.getConsole().log(LogLevel.values()[level], domain, message);
        Logger customLogger = Database.log.getCustom();
        if(customLogger != null) {
            customLogger.log(LogLevel.values()[level], domain, message);
        }
    }

    private static void recalculateLevels() {
        LogLevel callbackLevel = Database.log.getConsole().getLevel();
        Logger customLogger = Database.log.getCustom();
        if(customLogger != null && customLogger.getLevel().compareTo(callbackLevel) < 0) {
            callbackLevel = customLogger.getLevel();
        }

        if(CurrentLevel == callbackLevel) {
            return;
        }

        CurrentLevel = callbackLevel;
        final LogLevel finalLevel = callbackLevel;
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                // This cannot be done synchronously because it will deadlock
                // on the same mutex that is being held for this callback
                setCallbackLevel(finalLevel.getValue());
            }
        });
    }
}
