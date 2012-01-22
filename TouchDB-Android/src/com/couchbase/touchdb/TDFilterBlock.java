package com.couchbase.touchdb;

/**
 * Filter block, used in changes feeds and replication.
 */
public interface TDFilterBlock {

    boolean filter(TDRevision revision);

}
