package com.couchbase.touchdb;

public interface TDFilterBlock {

    boolean filter(TDRevision revision);

}
