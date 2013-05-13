package com.couchbase.cblite;

/**
 * Filter block, used in changes feeds and replication.
 */
public interface CBLFilterBlock {

    boolean filter(CBLRevision revision);

}
