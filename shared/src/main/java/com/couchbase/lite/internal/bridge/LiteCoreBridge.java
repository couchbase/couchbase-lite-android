package com.couchbase.lite.internal.bridge;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.litecore.LiteCoreException;

public class LiteCoreBridge {
    public static CouchbaseLiteException convertException(LiteCoreException orgEx) {
        return new CouchbaseLiteException(orgEx.domain, orgEx.code, orgEx);
    }
}
