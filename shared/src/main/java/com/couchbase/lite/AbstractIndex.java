package com.couchbase.lite;

import java.util.List;

abstract class AbstractIndex implements Index {
    abstract IndexType type();

    abstract String language();

    abstract boolean ignoreAccents();

    abstract List<Object> items();
}
