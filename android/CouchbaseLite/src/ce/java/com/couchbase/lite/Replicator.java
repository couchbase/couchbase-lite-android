package com.couchbase.lite;

import com.couchbase.lite.internal.replicator.CBLWebSocket;
import com.couchbase.litecore.C4Socket;

public final class Replicator extends AbstractReplicator {
    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config
     */
    public Replicator(ReplicatorConfiguration config) {
        super(config);
    }

    @Override
    void initC4Socket(int hash) {
        C4Socket.socketFactory.put(hash, CBLWebSocket.class);
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
