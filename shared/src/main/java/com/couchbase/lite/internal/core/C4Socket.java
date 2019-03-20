//
// C4Socket.java
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.internal.support.Log;


@SuppressWarnings("ConstantName")
public abstract class C4Socket {

    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    public static final String WEBSOCKET_SCHEME = "ws";
    public static final String WEBSOCKET_SECURE_CONNECTION_SCHEME = "wss";
    // Replicator option dictionary keys:
    public static final String kC4ReplicatorOptionExtraHeaders = "headers"; // Extra HTTP headers; string[]
    public static final String kC4ReplicatorOptionCookies = "cookies"; // HTTP Cookie header value; string
    public static final String kC4ReplicatorOptionAuthentication = "auth"; // Auth settings; Dict
    public static final String kC4ReplicatorOptionPinnedServerCert = "pinnedCert"; // Cert or public key [data]
    public static final String kC4ReplicatorOptionDocIDs = "docIDs"; // Docs to replicate; string[]
    public static final String kC4ReplicatorOptionChannels = "channels"; // SG channel names; string[]
    public static final String kC4ReplicatorOptionFilter = "filter"; // Filter name; string
    public static final String kC4ReplicatorOptionFilterParams = "filterParams"; // Filter params; Dict[string]
    public static final String kC4ReplicatorOptionSkipDeleted = "skipDeleted"; // Don't push/pull tombstones; bool
    public static final String kC4ReplicatorOptionNoIncomingConflicts = "noIncomingConflicts"; // Reject incoming
    // conflicts; bool
    public static final String kC4ReplicatorOptionOutgoingConflicts = "outgoingConflicts"; // Allow creating
    // conflicts on remote; bool
    public static final String kC4ReplicatorCheckpointInterval = "checkpointInterval"; // How often to checkpoint, in
    // seconds; number
    public static final String kC4ReplicatorOptionRemoteDBUniqueID = "remoteDBUniqueID"; // Stable ID for remote db
    // with unstable URL; string
    public static final String kC4ReplicatorHeartbeatInterval = "heartbeat"; // Interval in secs to send a keepalive
    // ping
    public static final String kC4ReplicatorResetCheckpoint = "reset";     // Start over w/o checkpoint; bool
    public static final String kC4ReplicatorOptionNoConflicts = "noConflicts"; // Puller rejects conflicts; bool
    public static final String kC4SocketOptionWSProtocols = "WS-Protocols"; // litecore::websocket::Provider
    // Auth dictionary keys:
    public static final String kC4ReplicatorAuthType = "type"; // Auth property; string
    // ::kProtocolsOption
    public static final String kC4ReplicatorAuthUserName = "username"; // Auth property; string
    public static final String kC4ReplicatorAuthPassword = "password"; // Auth property; string
    public static final String kC4ReplicatorAuthClientCert = "clientCert"; // Auth property; value platform-dependent
    // auth.type values:
    public static final String kC4AuthTypeBasic = "Basic"; // HTTP Basic (the default)
    public static final String kC4AuthTypeSession = "Session"; // SG session cookie
    public static final String kC4AuthTypeOpenIDConnect = "OpenID Connect";
    public static final String kC4AuthTypeFacebook = "Facebook";
    public static final String kC4AuthTypeClientCert = "Client Cert";
    // C4SocketFraming (C4SocketFactory.framing)
    public static final int kC4WebSocketClientFraming = 0; ///< Frame as WebSocket client messages (masked)
    public static final int kC4NoFraming = 1;              ///< No framing; use messages as-is
    public static final int kC4WebSocketServerFraming = 2; ///< Frame as WebSocket server messages (not masked)

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    //protected static String IMPLEMENTATION_CLASS_NAME;
    // Long: handle of C4Socket native address
    // C4Socket: Java class holds handle
    protected static final Map<Long, C4Socket> reverseLookupTable
        = Collections.synchronizedMap(new HashMap<>());

    // Map between SocketFactory Context and SocketFactory Class
    public static final Map<Object, Class> socketFactory
        = Collections.synchronizedMap(new HashMap<>());

    // Map between SocketFactory Context and Replicator
    public static final Map<Object, Replicator> socketFactoryContext
        = Collections.synchronizedMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    private static void open(
        long socket,
        Object socketFactoryContext,
        String scheme,
        String hostname,
        int port,
        String path,
        byte[] optionsFleece) {
        Log.w(LogDomain.NETWORK, "C4Socket.open() socket -> " + socket);
        final Class clazz = C4Socket.socketFactory.get(socketFactoryContext);
        if (clazz == null) {
            throw new IllegalArgumentException(String
                .format(Locale.ENGLISH, "Unknown SocketFactory UID -> %s", socketFactoryContext.toString()));
        }

        Log.w(LogDomain.NETWORK, "C4Socket.open() clazz -> " + clazz.getName());

        final Method method;
        try {
            method = clazz.getMethod(
                "socket_open",
                Long.TYPE,
                Object.class,
                String.class,
                String.class,
                Integer.TYPE,
                String.class,
                byte[].class);
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException("socket_open() method is not found in " + clazz, e);
        }
        try {
            method.invoke(null, socket, socketFactoryContext, scheme, hostname, port, path, optionsFleece);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException("socket_open() method is not accessible", e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException("socket_open() method throws Exception", e);
        }
    }

    private static void write(long handle, byte[] allocatedData) {
        if (handle == 0 || allocatedData == null) {
            Log.e(LogDomain.NETWORK, "C4Socket.callback.write() parameter error");
            return;
        }

        Log.w(LogDomain.NETWORK, "C4Socket.write() handle -> " + handle);

        final C4Socket socket = reverseLookupTable.get(handle);
        if (socket != null) { socket.send(allocatedData); }
        else { Log.w(LogDomain.NETWORK, "socket is null"); }
    }

    private static void completedReceive(long handle, long byteCount) {
        // NOTE: No further action is not required?
        Log.w(LogDomain.NETWORK, "C4Socket.completedReceive() handle -> " + handle);
    }

    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static void close(long handle) {
        // NOTE: close(long) method should not be called.
        Log.w(LogDomain.NETWORK, "C4Socket.close() handle -> " + handle);
        final C4Socket socket = reverseLookupTable.get(handle);
        if (socket != null) { socket.close(); }
        else { Log.w(LogDomain.NETWORK, "socket is null"); }
    }

    //-------------------------------------------------------------------------
    // Abstract methods
    //-------------------------------------------------------------------------

    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    private static void requestClose(long handle, int status, String message) {
        Log.w(LogDomain.NETWORK, "C4Socket.requestClose() handle -> " + handle);
        final C4Socket socket = reverseLookupTable.get(handle);
        if (socket != null) { socket.requestClose(status, message); }
        else { Log.w(LogDomain.NETWORK, "socket is null"); }
    }

    private static void dispose(long handle) {
        Log.w(LogDomain.NETWORK, "C4Socket.dispose() handle -> " + handle);
        // NOTE: close(long) method should not be called.
        final C4Socket socket = reverseLookupTable.get(handle);
        if (socket == null) { Log.w(LogDomain.NETWORK, "socket is null"); }
    }

    protected static native void gotHTTPResponse(long socket, int httpStatus, byte[] responseHeadersFleece);

    protected static native void opened(long socket);

    //-------------------------------------------------------------------------
    // callback methods from JNI
    //-------------------------------------------------------------------------

    protected static native void closed(long socket, int errorDomain, int errorCode, String message);

    protected static native void closeRequested(long socket, int status, String message);

    protected static native void completedWrite(long socket, long byteCount);

    protected static native void received(long socket, byte[] data);

    protected static native long fromNative(
        Object nativeHandle,
        String schema,
        String host,
        int port,
        String path,
        int framing);

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    protected long handle; // hold pointer to C4Socket

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------
    protected Object nativeHandle;

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------
    protected C4Socket() { this(0L); }

    protected C4Socket(long handle) {
        this.handle = handle;
    }

    protected abstract void send(byte[] allocatedData);

    // NOTE: Not used
    @SuppressWarnings("EmptyMethod")
    protected abstract void completedReceive(long byteCount);

    // NOTE: Not used
    @SuppressWarnings("EmptyMethod")
    protected abstract void close();

    protected abstract void requestClose(int status, String message);

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------
    protected void gotHTTPResponse(int httpStatus, byte[] responseHeadersFleece) {
        gotHTTPResponse(handle, httpStatus, responseHeadersFleece);
    }

    protected void completedWrite(long byteCount) {
        Log.w(LogDomain.NETWORK, "completedWrite(long) handle -> " + handle + ", byteCount -> " + byteCount);
        completedWrite(handle, byteCount);
    }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    long getHandle() {
        return handle;
    }
}
