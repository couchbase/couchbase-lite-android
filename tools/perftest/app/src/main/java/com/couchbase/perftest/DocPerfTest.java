package com.couchbase.perftest;

import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Document;
import com.couchbase.lite.MutableDocument;

public class DocPerfTest extends PerfTest{

    public DocPerfTest(DatabaseConfiguration dbConfig) {
        super(dbConfig);
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

    void addRevisions(final int revisions){
        try {
            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    try {
                        MutableDocument mDoc = new MutableDocument("doc");
                        for(int i = 0; i < revisions; i++){
                            mDoc.setValue("count", i);
                            db.save(mDoc);
                            Document doc = db.getDocument("doc");
                            mDoc = doc.toMutable();
                        }
                    }catch (CouchbaseLiteException e) {
                        e.printStackTrace();
                    }

                }
            });
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }
}
