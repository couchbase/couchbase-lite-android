package com.couchbase.cblite.replicator.changetracker;

import java.util.Map;

import com.couchbase.cblite.support.HttpClientFactory;

public interface TDChangeTrackerClient extends HttpClientFactory {

    void changeTrackerReceivedChange(Map<String,Object> change);

    void changeTrackerStopped(TDChangeTracker tracker);
}
