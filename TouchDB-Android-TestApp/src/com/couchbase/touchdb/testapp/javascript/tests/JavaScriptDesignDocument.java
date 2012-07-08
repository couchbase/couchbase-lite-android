package com.couchbase.touchdb.testapp.javascript.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.javascript.TDJavaScriptViewCompiler;
import com.couchbase.touchdb.testapp.tests.TouchDBTestCase;

public class JavaScriptDesignDocument extends TouchDBTestCase {

    public void testJavaScriptDesignDocument() {

        //register the javascript view compilter
        TDView.setCompiler(new TDJavaScriptViewCompiler());

        send(server, "PUT", "/db", TDStatus.CREATED, null);

        // PUT:
        Map<String,Object> doc1 = new HashMap<String,Object>();
        doc1.put("message", "hello");
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "PUT", "/db/doc1", doc1, TDStatus.CREATED, null);

        Map<String,Object> doc2 = new HashMap<String,Object>();
        doc2.put("message", "guten tag");
        Map<String,Object> result2 = (Map<String,Object>)sendBody(server, "PUT", "/db/doc2", doc2, TDStatus.CREATED, null);

        Map<String,Object> doc3 = new HashMap<String,Object>();
        doc3.put("message", "bonjour");
        Map<String,Object> result3 = (Map<String,Object>)sendBody(server, "PUT", "/db/doc3", doc3, TDStatus.CREATED, null);


        Map<String,Object> ddocViewTest = new HashMap<String,Object>();
        ddocViewTest.put("map", "function(doc) { if(doc.message) { emit(doc.message, 1); } }");

        Map<String,Object> ddocViews = new HashMap<String,Object>();
        ddocViews.put("test", ddocViewTest);

        Map<String,Object> ddoc = new HashMap<String,Object>();
        ddoc.put("views", ddocViews);
        Map<String,Object> ddocresult = (Map<String,Object>)sendBody(server, "PUT", "/db/_design/doc", ddoc, TDStatus.CREATED, null);

        // Build up our expected result

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("id", "doc1");
        row1.put("key", "hello");
        row1.put("value", 1.0);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("id", "doc2");
        row2.put("key", "guten tag");
        row2.put("value", 1.0);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("id", "doc3");
        row3.put("key", "bonjour");
        row3.put("value", 1.0);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        expectedRows.add(row3);
        expectedRows.add(row2);
        expectedRows.add(row1);

        Map<String,Object> expectedResult = new HashMap<String,Object>();
        expectedResult.put("offset", 0);
        expectedResult.put("total_rows", 3);
        expectedResult.put("rows", expectedRows);

        // Query the view and check the result:
        send(server, "GET", "/db/_design/doc/_view/test", TDStatus.OK, expectedResult);

    }

    public void testRealJavaScriptDesignDocument() {

        //register the javascript view compilter
        TDView.setCompiler(new TDJavaScriptViewCompiler());

        send(server, "PUT", "/rhinodb", TDStatus.CREATED, null);

        // PUT:
        Map<String,Object> doc1 = new HashMap<String,Object>();
        List<String> cat1 = new ArrayList();
        cat1.add("apple");
        cat1.add("bannana");
        doc1.put("categories", cat1);
        Map<String,Object> result = (Map<String,Object>)sendBody(server, "PUT", "/rhinodb/doc1", doc1, TDStatus.CREATED, null);

        Map<String,Object> doc2 = new HashMap<String,Object>();
        List<String> cat2 = new ArrayList();
        cat2.add("clock");
        cat2.add("dill");
        doc2.put("categories", cat2);
        Map<String,Object> result2 = (Map<String,Object>)sendBody(server, "PUT", "/rhinodb/doc2", doc2, TDStatus.CREATED, null);

        Map<String,Object> doc3 = new HashMap<String,Object>();
        List<String> cat3 = new ArrayList();
        cat3.add("elephant");
        cat3.add("fun");
        doc3.put("categories", cat3);
        Map<String,Object> result3 = (Map<String,Object>)sendBody(server, "PUT", "/rhinodb/doc3", doc3, TDStatus.CREATED, null);


        Map<String,Object> ddocViewTest = new HashMap<String,Object>();
        ddocViewTest.put("map", "function(doc) { if (doc.categories) { for (i in doc.categories) { emit(doc.categories[i], 1); } } }");

        Map<String,Object> ddocViews = new HashMap<String,Object>();
        ddocViews.put("test", ddocViewTest);

        Map<String,Object> ddoc = new HashMap<String,Object>();
        ddoc.put("views", ddocViews);
        Map<String,Object> ddocresult = (Map<String,Object>)sendBody(server, "PUT", "/rhinodb/_design/doc", ddoc, TDStatus.CREATED, null);

        // Build up our expected result

        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("id", "doc1");
        row1.put("key", "apple");
        row1.put("value", 1.0);

        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("id", "doc1");
        row2.put("key", "bannana");
        row2.put("value", 1.0);

        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("id", "doc2");
        row3.put("key", "clock");
        row3.put("value", 1.0);

        Map<String,Object> row4 = new HashMap<String,Object>();
        row4.put("id", "doc2");
        row4.put("key", "dill");
        row4.put("value", 1.0);

        Map<String,Object> row5 = new HashMap<String,Object>();
        row5.put("id", "doc3");
        row5.put("key", "elephant");
        row5.put("value", 1.0);

        Map<String,Object> row6 = new HashMap<String,Object>();
        row6.put("id", "doc3");
        row6.put("key", "fun");
        row6.put("value", 1.0);

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();

        expectedRows.add(row1);
        expectedRows.add(row2);
        expectedRows.add(row3);
        expectedRows.add(row4);
        expectedRows.add(row5);
        expectedRows.add(row6);

        Map<String,Object> expectedResult = new HashMap<String,Object>();
        expectedResult.put("offset", 0);
        expectedResult.put("total_rows", 6);
        expectedResult.put("rows", expectedRows);

        // Query the view and check the result:
        Object res = send(server, "GET", "/rhinodb/_design/doc/_view/test", TDStatus.OK, expectedResult);
    }

}
