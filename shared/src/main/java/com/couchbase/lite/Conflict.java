package com.couchbase.lite;

public interface Conflict {
    ReadOnlyDocument getMine();

    ReadOnlyDocument getTheirs();

    ReadOnlyDocument getBase();
}
