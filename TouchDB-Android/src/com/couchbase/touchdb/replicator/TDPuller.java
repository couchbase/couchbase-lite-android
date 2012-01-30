package com.couchbase.touchdb.replicator;

import java.net.URL;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.support.HttpClientFactory;

public class TDPuller extends TDReplicator {

    public TDPuller(TDDatabase db, URL remote, boolean continuous) {
        this(db, remote, continuous, null);
        // TODO Auto-generated constructor stub
    }
    
    public TDPuller(TDDatabase db, URL remote, boolean continuous, HttpClientFactory clientFactory) {
        super(db, remote, continuous, clientFactory);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void beginReplicating() {
        // TODO Auto-generated method stub

    }

}
