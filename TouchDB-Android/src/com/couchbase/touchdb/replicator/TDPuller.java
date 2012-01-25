package com.couchbase.touchdb.replicator;

import java.net.URL;

import com.couchbase.touchdb.TDDatabase;

public class TDPuller extends TDReplicator {

    public TDPuller(TDDatabase db, URL remote, boolean continuous) {
        super(db, remote, continuous);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void beginReplicating() {
        // TODO Auto-generated method stub

    }

}
