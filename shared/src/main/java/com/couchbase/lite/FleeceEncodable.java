package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLEncoder;

interface FleeceEncodable {
    void fleeceEncode(FLEncoder encoder, Database database) throws CouchbaseLiteException;
}
