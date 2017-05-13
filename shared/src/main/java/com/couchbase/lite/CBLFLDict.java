package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

/*package*/ class CBLFLDict {
    private FLDict flDict;
    private CBLC4Doc c4doc;
    private Database database;

    /*package*/ CBLFLDict(FLDict dict, CBLC4Doc c4doc, Database database) {
        this.flDict = dict;
        this.c4doc = c4doc;
        this.database = database;
    }

    /*package*/ FLDict getFlDict() {
        return flDict;
    }

    /*package*/ CBLC4Doc getC4doc() {
        return c4doc;
    }

    /*package*/ Database getDatabase() {
        return database;
    }
}
