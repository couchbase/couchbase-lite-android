package com.couchbase.touchdb.support;

import java.util.List;

public interface TDBatchProcessor<T> {

    void process(List<T> inbox);

}
