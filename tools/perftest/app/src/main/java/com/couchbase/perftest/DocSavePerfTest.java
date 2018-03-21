package com.couchbase.perftest;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Document;
import com.couchbase.lite.MutableDocument;

import java.util.Locale;
import java.util.Map;

public class DocSavePerfTest extends PerfTest {
    protected DocSavePerfTest(Context context, DatabaseConfiguration dbConfig) {
        super(context, dbConfig);
    }

    @Override
    protected void test() {
        final int numDocs    = 1000;  // 1K docs in one batch
        final int numUpdates = 10000; // 10K updates -> 10M revisions


        Log.i(TAG, String.format("--- Creating %d documents and update %s times ---", numDocs, numUpdates));
        measure(numDocs * numUpdates, "save", new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numUpdates; i++) {
                    Log.w(TAG, String.format("Test Batch %d of %d", i, numUpdates));
                    Map<String, Map<String, Object>> products = TestData.generateProducts(numDocs);
                    saveDocument("Products", products);
                }
            }
        });
    }

    void saveDocument(final String documentStoreName, final Map<String, Map<String, Object>> newDocs) {
        Log.w(TAG, String.format("Saving document list (%d) in document store {%s}...", newDocs.size(), documentStoreName));
        try {
            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (String key : newDocs.keySet()) {
                            String docID = getDocID(documentStoreName, key);
                            MutableDocument doc = updateMutableDocument(docID, newDocs.get(key));
                            db.save(doc);
                        }
                    } catch (CouchbaseLiteException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    private String getDocID(String documentStoreName, String id) {
        return String.format(Locale.ENGLISH, "%s|%s", documentStoreName.toLowerCase(), id);
    }

    private MutableDocument updateMutableDocument(String docID, Map<String, Object> value) {
        Document doc = db.getDocument(docID);
        MutableDocument mutableDoc = null;
        if (doc != null) {
            mutableDoc = doc.toMutable();
            mutableDoc.setData(value);
        } else
            mutableDoc = new MutableDocument(docID, value);
        return mutableDoc;
    }
}
