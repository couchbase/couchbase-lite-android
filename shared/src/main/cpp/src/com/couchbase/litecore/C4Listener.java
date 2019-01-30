//
// C4Listener.java
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

public class C4Listener {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle = 0L; // hold pointer to C4Listener

    //-------------------------------------------------------------------------
    // public static methods
    //-------------------------------------------------------------------------

    public static int getAvailableAPIs() {
        return availableAPIs();
    }

    public static String getURINameFromPath(String path) {
        return uriNameFromPath(path);
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public C4Listener(C4ListenerConfig config) throws LiteCoreException {
        if (config == null)
            throw new IllegalArgumentException();

        handle = start(
                config.getPort(),
                config.getApis(),
                config.getDirectory(),
                config.isAllowCreateDBs(),
                config.isAllowDeleteDBs(),
                config.isAllowPush(),
                config.isAllowPull());
    }

    public void free() {
        if (handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    public boolean shareDB(String name, C4Database db) {
        return shareDB(handle, name, db.getHandle());
    }

    public boolean unshareDB(String name) {
        return unshareDB(handle, name);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------
    static native int availableAPIs();

    static native long start(int port,
                             int apis,
                             String directory,
                             boolean allowCreateDBs,
                             boolean allowDeleteDBs,
                             boolean allowPush, boolean allowPull) throws LiteCoreException;

    static native void free(long listener);

    /**
     * Makes a database available from the network, under the given name.
     */
    static native boolean shareDB(long listener, String name, long db);

    /**
     * Makes a previously-shared database unavailable.
     */
    static native boolean unshareDB(long listener, String name);

    /**
     * A convenience that, given a filesystem path to a database, returns the database name
     * for use in an HTTP URI path.
     */
    static native String uriNameFromPath(String path);
}
