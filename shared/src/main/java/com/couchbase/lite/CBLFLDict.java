package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

class CBLFLDict {
    private FLDict flDict;
    private CBLC4Doc c4doc;
    private Database database;

    CBLFLDict(FLDict dict, CBLC4Doc c4doc, Database database) {
        this.flDict = dict;
        this.c4doc = c4doc;
        this.database = database;
    }

    FLDict getFlDict() {
        return flDict;
    }

    CBLC4Doc getC4doc() {
        return c4doc;
    }

    Database getDatabase() {
        return database;
    }
}
