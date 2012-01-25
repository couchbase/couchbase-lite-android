package com.couchbase.touchdb.replicator.changetracker;

import java.util.Map;

public interface TDChangeTrackerClient {

    void changeTrackerReceivedChange(Map<String,Object> change);

    void changeTrackerStopped(TDChangeTracker tracker);

}
