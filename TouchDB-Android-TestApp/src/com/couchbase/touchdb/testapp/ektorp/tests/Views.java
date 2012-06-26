package com.couchbase.touchdb.testapp.ektorp.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ViewQuery;
import org.ektorp.ViewResult;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.TDViewMapBlock;
import com.couchbase.touchdb.TDViewMapEmitBlock;
import com.couchbase.touchdb.TDViewReduceBlock;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class Views extends AndroidTestCase {

    //static inializer to ensure that touchdb:// URLs are handled properly
    {
        TDURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    public static final String dDocName = "ddoc";
    public static final String dDocId = "_design/" + dDocName;
    public static final String viewName = "aview";
    public static final String viewReduceName = "aviewreduce";

    public void putDocs(CouchDbConnector db) {

        TestObject obj2 = new TestObject("22222", "two");
        db.create(obj2);

        TestObject obj4 = new TestObject("44444", "four");
        db.create(obj4);

        TestObject obj1 = new TestObject("11111", "one");
        db.create(obj1);

        TestObject obj3 = new TestObject("33333", "three");
        db.create(obj3);

        TestObject obj5 = new TestObject("55555", "five");
        db.create(obj5);

    }

    public static TDView createView(TDDatabase db) {
        TDView view = db.getViewNamed(String.format("%s/%s", dDocName, viewName));
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if(document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, null, "1");
        return view;
    }

    public static TDView createViewWithReduce(TDDatabase db) {
        TDView view = db.getViewNamed(String.format("%s/%s", dDocName, viewReduceName));
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if(document.get("key") != null) {
                    emitter.emit(document.get("key"), 1);
                }
            }
        }, new TDViewReduceBlock() {

            @Override
            public Object reduce(List<Object> keys, List<Object> values,
                    boolean rereduce) {
                return TDView.totalValues(values);
            }
        }, "1");
        return view;
    }

    public void testViewQuery() throws IOException {

        String filesDir = getContext().getFilesDir().getAbsolutePath();
        TDServer tdserver = new TDServer(filesDir);

        //ensure the test is repeatable
        TDDatabase old = tdserver.getExistingDatabaseNamed("ektorp_views_test");
        if(old != null) {
            old.deleteDatabase();
        }

        HttpClient httpClient = new TouchDBHttpClient(tdserver);
        CouchDbInstance server = new StdCouchDbInstance(httpClient);

        CouchDbConnector db = server.createConnector("ektorp_views_test", true);
        TDDatabase tdDb = tdserver.getExistingDatabaseNamed("ektorp_views_test");

        putDocs(db);
        createView(tdDb);

        ViewQuery query = new ViewQuery().designDocId(dDocId).viewName(viewName);
        ViewResult result = db.queryView(query);
        Assert.assertEquals(5, result.getTotalRows());
        Assert.assertEquals("five", result.getRows().get(0).getKey());
        Assert.assertEquals("four", result.getRows().get(1).getKey());
        Assert.assertEquals("one", result.getRows().get(2).getKey());
        Assert.assertEquals("three", result.getRows().get(3).getKey());
        Assert.assertEquals("two", result.getRows().get(4).getKey());

        // Start/end key query:
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).startKey("a").endKey("one");
        result = db.queryView(query);
        Assert.assertEquals(3, result.getTotalRows());
        Assert.assertEquals("five", result.getRows().get(0).getKey());
        Assert.assertEquals("four", result.getRows().get(1).getKey());
        Assert.assertEquals("one", result.getRows().get(2).getKey());

        // Start/end query without inclusive end:
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).startKey("a").endKey("one").inclusiveEnd(false);
        result = db.queryView(query);
        Assert.assertEquals(2, result.getTotalRows());
        Assert.assertEquals("five", result.getRows().get(0).getKey());
        Assert.assertEquals("four", result.getRows().get(1).getKey());

        // Reversed:
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).startKey("o").endKey("five").inclusiveEnd(true).descending(true);
        result = db.queryView(query);
        Assert.assertEquals(2, result.getTotalRows());
        Assert.assertEquals("four", result.getRows().get(0).getKey());
        Assert.assertEquals("five", result.getRows().get(1).getKey());

        // Reversed, no inclusive end:
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).startKey("o").endKey("five").inclusiveEnd(false).descending(true);
        result = db.queryView(query);
        Assert.assertEquals(1, result.getTotalRows());
        Assert.assertEquals("four", result.getRows().get(0).getKey());

        // Specific keys:
        List<String> keys = new ArrayList<String>();
        keys.add("two");
        keys.add("four");
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).keys(keys);
        result = db.queryView(query);
        Assert.assertEquals(2, result.getTotalRows());
        Assert.assertEquals("four", result.getRows().get(0).getKey());
        Assert.assertEquals("two", result.getRows().get(1).getKey());

        // Limit
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).limit(3);
        result = db.queryView(query);
        Assert.assertEquals(3, result.getTotalRows());
        Assert.assertEquals("five", result.getRows().get(0).getKey());
        Assert.assertEquals("four", result.getRows().get(1).getKey());
        Assert.assertEquals("one", result.getRows().get(2).getKey());

        // Limit & Skip
        query = new ViewQuery().designDocId(dDocId).viewName(viewName).limit(2).skip(1);
        result = db.queryView(query);
        Assert.assertEquals(2, result.getTotalRows());
        Assert.assertEquals("four", result.getRows().get(0).getKey());
        Assert.assertEquals("one", result.getRows().get(1).getKey());
    }

    public void testViewReduceQuery() throws IOException {

        String filesDir = getContext().getFilesDir().getAbsolutePath();
        TDServer tdserver = new TDServer(filesDir);

        //ensure the test is repeatable
        TDDatabase old = tdserver.getExistingDatabaseNamed("ektorp_views_test");
        if(old != null) {
            old.deleteDatabase();
        }

        HttpClient httpClient = new TouchDBHttpClient(tdserver);
        CouchDbInstance server = new StdCouchDbInstance(httpClient);

        CouchDbConnector db = server.createConnector("ektorp_views_test", true);
        TDDatabase tdDb = tdserver.getExistingDatabaseNamed("ektorp_views_test");

        putDocs(db);
        createViewWithReduce(tdDb);

        ViewQuery query = new ViewQuery().designDocId(dDocId).viewName(viewReduceName).reduce(true);
        ViewResult result = db.queryView(query);

        Assert.assertEquals(1, result.getTotalRows());
        Assert.assertEquals(5, result.getRows().get(0).getValueAsInt());
    }
}
