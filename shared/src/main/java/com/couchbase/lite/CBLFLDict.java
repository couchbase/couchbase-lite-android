package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

class CBLFLDict {
    private FLDict flDict;
    private CBLFLDataSource lflDataSource;
    private Database database;

    CBLFLDict(FLDict dict, CBLFLDataSource lflDataSource, Database database) {
        this.flDict = dict;
        this.lflDataSource = lflDataSource;
        this.database = database;
    }

    FLDict getFlDict() {
        return flDict;
    }

    CBLFLDataSource getLflDataSource() {
        return lflDataSource;
    }

    Database getDatabase() {
        return database;
    }
}
