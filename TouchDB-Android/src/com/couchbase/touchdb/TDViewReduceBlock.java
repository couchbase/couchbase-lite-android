package com.couchbase.touchdb;

import java.util.List;

public interface TDViewReduceBlock {

    Object reduce(List<Object> keys, List<Object> values, boolean rereduce);

}
