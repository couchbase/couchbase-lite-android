package com.couchbase.cblite.support;

import java.util.List;

public interface CBLBatchProcessor<T> {

    void process(List<T> inbox);

}
