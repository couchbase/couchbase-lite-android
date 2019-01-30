//
// C4DatabaseObserver.java
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class C4DatabaseObserver {
    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------
    // Long: handle of C4DatabaseObserver native address
    // C4DatabaseObserver: Java class holds handle
    private static Map<Long, C4DatabaseObserver> reverseLookupTable
            = Collections.synchronizedMap(new HashMap<Long, C4DatabaseObserver>());

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle = 0L; // hold pointer to C4DatabaseObserver
    private C4DatabaseObserverListener listener = null;
    private Object context = null;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4DatabaseObserver(long db, C4DatabaseObserverListener listener, Object context) {
        this.listener = listener;
        this.context = context;
        this.handle = create(db);
        this.reverseLookupTable.put(handle, this);
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public C4DatabaseChange[] getChanges(int maxChanges) {
        return getChanges(handle, maxChanges);
    }

    public void free() {
        if (handle != 0L) {
            reverseLookupTable.remove(handle);
            free(handle);
            handle = 0L;
        }
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
    // callback methods from JNI
    //-------------------------------------------------------------------------

    /**
     * Callback invoked by a database observer.
     * <p>
     * NOTE: Two parameters, observer and context, which are defined for iOS:
     * observer -> this instance
     * context ->  maintained in java layer
     */
    private static void callback(long handle) {
        C4DatabaseObserver obs = reverseLookupTable.get(handle);
        if (obs != null && obs.listener != null)
            obs.listener.callback(obs, obs.context);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native long create(long db);

    static native C4DatabaseChange[] getChanges(long observer, int maxChanges);

    static native void free(long c4observer);
}
