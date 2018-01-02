package com.couchbase.lite;

import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.MContext;

public class DocContext extends MContext {
    private Database _db;

    private C4Document _doc;

    DocContext(Database db, C4Document doc) {
        super(new AllocSlice("{}".getBytes()), db.getSharedKeys().getFLSharedKeys());
        _db = db;
        _doc = doc;
    }

    public Database getDatabase() {
        return _db;
    }

    public C4Document getDoc() {
        return _doc;
    }
}
