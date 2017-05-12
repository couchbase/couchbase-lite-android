package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

public class CBLFLDict {
    private FLDict flDict;
    private com.couchbase.litecore.Document c4doc;
    private Database database;

    public CBLFLDict(FLDict dict, com.couchbase.litecore.Document c4doc, Database database) {
        this.flDict = dict;
        this.c4doc = c4doc;
        this.database = database;
    }

    public FLDict getFlDict() {
        return flDict;
    }

    public com.couchbase.litecore.Document getC4doc() {
        return c4doc;
    }

    public Database getDatabase() {
        return database;
    }
}
