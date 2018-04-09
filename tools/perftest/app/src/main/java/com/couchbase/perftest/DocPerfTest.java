package com.couchbase.perftest;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.MutableDocument;

public class DocPerfTest extends PerfTest {

    public DocPerfTest(Context context, DatabaseConfiguration dbConfig) {
        super(context, dbConfig);
    }

    @Override
    protected void test() {
        final int revs = 10000;
        Log.i(TAG, String.format("--- Creating %d revisions ---", revs));
        measure(revs, "revision", new Runnable() {
            @Override
            public void run() {
                addRevisions(revs);
            }
        });
    }

    void addRevisions(final int revisions) {
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                try {
                    MutableDocument mDoc = new MutableDocument("doc");
                    updateDoc(mDoc, revisions);
                    //updateDocWithGetDocument(mDoc, revisions);
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    void updateDoc(MutableDocument doc, final int revisions) throws CouchbaseLiteException {
        for (int i = 0; i < revisions; i++) {
            doc.setValue("count", i);
            db.save(doc);
        }
    }

    void updateDocWithGetDocument(MutableDocument doc, final int revisions) throws CouchbaseLiteException {
        for (int i = 0; i < revisions; i++) {
            doc.setValue("count", i);
            db.save(doc);
            doc = db.getDocument("doc").toMutable();
        }
    }
}
