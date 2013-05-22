package com.couchbase.cblite.replicator.changetracker;

import java.util.Map;

import com.couchbase.cblite.support.HttpClientFactory;

public interface CBLChangeTrackerClient extends HttpClientFactory {

    void changeTrackerReceivedChange(Map<String,Object> change);

    void changeTrackerStopped(CBLChangeTracker tracker);
}
