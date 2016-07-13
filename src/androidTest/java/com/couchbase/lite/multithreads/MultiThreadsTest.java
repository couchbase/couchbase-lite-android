package com.couchbase.lite.multithreads;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.View;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hideki on 6/29/16.
 */
public class MultiThreadsTest  extends LiteTestCaseWithDB {
    private static final String TAG = MultiThreadsTest.class.getName();

    @Override
    protected void setUp() throws Exception {
        if (!multithreadsTestsEnabled()) {
            return;
        }

        super.setUp();
    }

    public void testInsertAndQueryThreads(){

        if (!multithreadsTestsEnabled()) {
            return;
        }

        // create view
        final View view = createView(database);

        final AtomicInteger count = new AtomicInteger();
        Thread insertThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < 500; i++) {
                    String docID = String.format(Locale.ENGLISH, "docID-%08d", count.incrementAndGet());
                    Document doc = database.getDocument(docID);
                    Map<String, Object> props = new HashMap<>();
                    props.put("key", count.get());
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Document.putProperty(). ", e);
                        fail(e.getMessage());
                    }
                }
            }
        });

        Thread queryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < 100; i++) {
                    Query query = view.createQuery();
                    try {
                        for (QueryRow row : query.run()) {
                            Log.i(TAG, "row=%s", row.getDocumentId());
                        }
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Query.run(). ", e);
                        fail(e.getMessage());
                    }
                }
            }
        });

        queryThread.start();
        insertThread.start();

        try {
            insertThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in insertThread. ", e);
        }
        try {
            queryThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in insertThread. ", e);
        }
    }

    public static View createView(Database db) {
        return createView(db, "aview");
    }

    public static View createView(Database db, String name) {
        View view = db.getView(name);
        if (view != null) {
            view.setMapReduce(new Mapper() {
                @Override
                public void map(Map<String, Object> document, Emitter emitter) {
                    if (document.get("key") != null)
                        emitter.emit(document.get("key"), null);
                }
            }, null, "1");
        }
        return view;
    }
}
