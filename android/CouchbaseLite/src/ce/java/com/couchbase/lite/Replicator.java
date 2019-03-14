package com.couchbase.lite;

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.replicator.CBLWebSocket;


public final class Replicator extends AbstractReplicator {
    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config
     */
    public Replicator(@NonNull ReplicatorConfiguration config) {
        super(config);
    }

    @Override
    void initSocketFactory(Object socketFactoryContext) {
        C4Socket.socketFactory.put(socketFactoryContext, CBLWebSocket.class);
    }

    @Override
    int framing() {
        return C4Socket.kC4NoFraming;
    }

    @Override
    String schema() {
        return null;
    }
}
