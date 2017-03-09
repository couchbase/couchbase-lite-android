package com.couchbase.lite.internal.bridge;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.litecore.LiteCoreException;

/**
 * Created by hideki on 3/8/17.
 */

public class LiteCoreBridge {
    public static CouchbaseLiteException convertException(LiteCoreException orgEx) {
        String msg = "TODO: Need to implement"; //TODO
        return new CouchbaseLiteException(orgEx.domain, orgEx.code, msg, orgEx);
    }
}
