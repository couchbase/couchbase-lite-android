package com.couchbase.cblite.support;

import java.util.List;

public interface TDBatchProcessor<T> {

    void process(List<T> inbox);

}
