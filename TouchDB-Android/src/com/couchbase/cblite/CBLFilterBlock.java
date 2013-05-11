package com.couchbase.cblite;

/**
 * Filter block, used in changes feeds and replication.
 */
public interface TDFilterBlock {

    boolean filter(TDRevision revision);

}
