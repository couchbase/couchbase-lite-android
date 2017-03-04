package com.couchbase.lite;

import java.util.Map;

public interface ConflictResolver {
    Map<String, Object> resolve(Map<String, Object> mine, Map<String, Object> theirs, Map<String, Object> base);
}
