package com.couchbase.lite;

import com.couchbase.litecore.C4QueryEnumerator;

public class QueryResultContext extends DocContext {

    private C4QueryEnumerator _enumerator;

    public QueryResultContext(Database db, C4QueryEnumerator enumerator) {
        super(db);
        _enumerator = enumerator;
    }


    @Override
    protected void finalize() throws Throwable {
        if (_enumerator != null)
            _enumerator.free();
        super.finalize();
    }

    public C4QueryEnumerator getEnumerator() {
        return _enumerator;
    }
}
