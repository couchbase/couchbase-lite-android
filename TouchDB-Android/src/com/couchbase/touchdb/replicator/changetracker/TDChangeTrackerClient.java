package com.couchbase.touchdb.replicator.changetracker;

import java.util.Map;

import com.couchbase.touchdb.support.HttpClientFactory;

public interface TDChangeTrackerClient extends HttpClientFactory {

    void changeTrackerReceivedChange(Map<String,Object> change);

    void changeTrackerStopped(TDChangeTracker tracker);
}
