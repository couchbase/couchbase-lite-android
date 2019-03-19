//
// C4Replicator.java
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

package com.couchbase.lite.internal.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4Replicator {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    public static final String C4_REPLICATOR_SCHEME_2 = "blip";
    public static final String C4_REPLICATOR_TLS_SCHEME_2 = "blips";

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------
    // Long: handle of C4Replicator native address
    // C4Replicator: Java class holds handle
    private static final Map<Long, C4Replicator> reverseLookupTable
        = Collections.synchronizedMap(new HashMap<>());

    private static final Map<Object, C4Replicator> contextToC4ReplicatorMap
        = Collections.synchronizedMap(new HashMap<>());

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    public static boolean mayBeTransient(C4Error err) {
        return mayBeTransient(err.getDomain(), err.getCode(), err.getInternalInfo());
    }

    public static boolean mayBeNetworkDependent(C4Error err) {
        return mayBeNetworkDependent(err.getDomain(), err.getCode(), err.getInternalInfo());
    }

    private static void statusChangedCallback(long handle, C4ReplicatorStatus status) {
        final C4Replicator repl = reverseLookupTable.get(handle);
        if (repl != null && repl.listener != null) {
            repl.listener.statusChanged(
                repl,
                status,
                repl.replicatorContext);
        }
    }

    private static void documentEndedCallback(long handle, boolean pushing, C4DocumentEnded[] documentsEnded) {
        final C4Replicator repl = reverseLookupTable.get(handle);
        Log.d(LogDomain.REPLICATOR, "documentErrorCallback() handle -> " + handle + ", pushing -> " + pushing);

        if (repl != null && repl.listener != null) {
            repl.listener.documentEnded(repl, pushing, documentsEnded,
                repl.replicatorContext);
        }
    }

    private static boolean validationFunction(String docID, int flags, long dict, boolean isPush, Object context) {
        final C4Replicator repl = contextToC4ReplicatorMap.get(context);
        if (repl != null) {
            if (isPush && repl.pushFilter != null) {
                return repl.pushFilter.validationFunction(
                    docID,
                    flags,
                    dict,
                    isPush,
                    repl.replicatorContext);
            }
            else if (!isPush && repl.pullFilter != null) {
                return repl.pullFilter.validationFunction(
                    docID,
                    flags,
                    dict,
                    isPush,
                    repl.replicatorContext);
            }
        }
        return true;
    }

    /**
     * Creates a new replicator.
     */
    static native long create(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        long otherLocalDB,
        int push,
        int pull,
        Object socketFactoryContext,
        int framing,
        Object replicatorContext,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        byte[] options) throws LiteCoreException;

    /**
     * Creates a new replicator from an already-open C4Socket. This is for use by listeners
     * that accept incoming connections, wrap them by calling `c4socket_fromNative()`, then
     * start a passive replication to service them.
     *
     * @param db                The local database.
     * @param openSocket        An already-created C4Socket.
     * @param push
     * @param pull
     * @param replicatorContext
     * @param options
     * @return The pointer of the newly created replicator
     */
    static native long createWithSocket(
        long db,
        long openSocket,
        int push,
        int pull,
        Object replicatorContext,
        byte[] options) throws LiteCoreException;

    /**
     * Frees a replicator reference. If the replicator is running it will stop.
     */
    static native void free(long replicator, Object replicatorContext, Object socketFactoryContext);

    /**
     * Tells a replicator to stop.
     */
    static native void stop(long replicator);

    /**
     * Returns the current state of a replicator.
     */
    static native C4ReplicatorStatus getStatus(long replicator);

    /**
     * Returns the HTTP response headers as a Fleece-encoded dictionary.
     */
    static native byte[] getResponseHeaders(long replicator);

    /**
     * Returns true if this is a network error that may be transient,
     * i.e. the client should retry after a delay.
     */
    static native boolean mayBeTransient(int domain, int code, int info);

    //-------------------------------------------------------------------------
    // public static methods
    //-------------------------------------------------------------------------

    /**
     * Returns true if this error might go away when the network environment changes,
     * i.e. the client should retry after notification of a network status change.
     */
    static native boolean mayBeNetworkDependent(int domain, int code, int info);


    private long handle; // hold pointer to C4Replicator

    private C4ReplicatorListener listener;

    private C4ReplicationFilter pushFilter;
    private C4ReplicationFilter pullFilter;
    private Object replicatorContext;

    private Object socketFactoryContext;

    C4Replicator(
        long db,
        String schema,
        String host,
        int port,
        String path,
        String remoteDatabaseName,
        long otherLocalDB,
        int push,
        int pull,
        byte[] options,
        C4ReplicatorListener listener,
        C4ReplicationFilter pushFilter,
        C4ReplicationFilter pullFilter,
        Object replicatorContext,
        Object socketFactoryContext,
        int framing) throws LiteCoreException {
        this.listener = listener;
        this.replicatorContext = replicatorContext;
        this.socketFactoryContext = socketFactoryContext;
        this.pushFilter = pushFilter;
        this.pullFilter = pullFilter;

        contextToC4ReplicatorMap.put(replicatorContext, this);

        handle = create(db, schema, host, port, path, remoteDatabaseName,
            otherLocalDB,
            push, pull,
            socketFactoryContext,
            framing,
            replicatorContext,
            pushFilter,
            pullFilter,
            options);

        reverseLookupTable.put(handle, this);
    }

    C4Replicator(
        long db,
        long openSocket,
        int push,
        int pull,
        byte[] options,
        C4ReplicatorListener listener,
        Object replicatorContext) throws LiteCoreException {
        this.listener = listener;
        this.replicatorContext = replicatorContext;
        handle = createWithSocket(
            db,
            openSocket,
            push,
            pull,
            replicatorContext,
            options);
        reverseLookupTable.put(handle, this);
    }

    // !!FIXME: There appears to be a heisenbug, here:
    // hbase.lite.tes: JNI ERROR (app bug): attempt to use stale Global 0x25e6 (should be 0x25ea)
    // hbase.lite.tes: java_vm_ext.cc:542] JNI DETECTED ERROR IN APPLICATION: use of deleted global reference 0x25e6
    // hbase.lite.tes: java_vm_ext.cc:542]     from void com.couchbase.lite.internal.core.C4Replicator.free(
    //                                             long, java.lang.Object, java.lang.Object)
    // It was introduced during the massive refactor to clean up the code, 3/13/2019
    public void free() {
        if (handle != 0L) {
            Log.d(LogDomain.REPLICATOR, "handle: " + handle);
            Log.d(
                LogDomain.REPLICATOR,
                "replicatorContext: " + replicatorContext + " $" + replicatorContext.getClass());
            Log.d(
                LogDomain.REPLICATOR,
                "socketFactoryContext: " + socketFactoryContext + " $" + socketFactoryContext.getClass());
            free(handle, replicatorContext, socketFactoryContext);
            handle = 0L;
        }

        if (replicatorContext != null) {
            contextToC4ReplicatorMap.remove(this.replicatorContext);
            replicatorContext = null;
        }
    }

    public void stop() {
        if (handle != 0L) { stop(handle); }
    }

    public C4ReplicatorStatus getStatus() {
        return (handle == 0L) ? null : getStatus(handle);
    }

    public byte[] getResponseHeaders() {
        return (handle == 0L) ? null : getResponseHeaders(handle);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }
}
