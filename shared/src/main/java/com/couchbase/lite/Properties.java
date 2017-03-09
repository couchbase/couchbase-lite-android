package com.couchbase.lite;

import java.util.Map;

interface Properties {
    Map<String, Object> getProperties();

    void setProperties(Map<String, Object> properties);
}
