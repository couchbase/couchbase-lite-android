package com.couchbase.lite;

import com.couchbase.lite.mockserver.MockCheckpointGet;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.router.URLConnection;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RouterTest extends LiteTestCaseWithDB {

    public static final String TAG = "Router";

    // - (void) test_Server in Router_Tests.m
    public void testServer() {
        Map<String, Object> responseBody = new HashMap<String, Object>();
        responseBody.put("CBLite", "Welcome");
        responseBody.put("couchdb", "Welcome");
        responseBody.put("version", com.couchbase.lite.router.Router.getVersionString());
        send("GET", "/", Status.OK, responseBody);

        Map<String, Object> session = new HashMap<String, Object>();
        Map<String, Object> userCtx = new HashMap<String, Object>();
        List<String> roles = new ArrayList<String>();
        roles.add("_admin");
        session.put("ok", true);
        userCtx.put("name", null);
        userCtx.put("roles", roles);
        session.put("userCtx", userCtx);
        send("GET", "/_session", Status.OK, session);

        List<String> allDbs = new ArrayList<String>();
        allDbs.add("cblite-test");
        send("GET", "/_all_dbs", Status.OK, allDbs);

        send("GET", "/non-existant", Status.NOT_FOUND, null);
        send("GET", "/BadName", Status.BAD_REQUEST, null);
        Map<String, Object> expectedBody = new HashMap<String, Object>();
        expectedBody.put("error", "method_not_allowed");
        expectedBody.put("status", 405);
        send("PUT", "/", Status.METHOD_NOT_ALLOWED, expectedBody);
        send("POST", "/", Status.METHOD_NOT_ALLOWED, expectedBody);
    }

    // - (void) test_Databases in Router_Tests.m
    public void testDatabase() {
        send("PUT", "/database", Status.CREATED, null);
        Map entries = new HashMap<String, Map<String, Object>>();

        entries.put("results", new ArrayList<Object>());
        entries.put("last_seq", 0);

        send("GET", "/database/_changes?feed=normal&heartbeat=300000&style=all_docs", Status.OK, entries);

        Map<String, Object> dbInfo = (Map<String, Object>) send("GET", "/database", Status.OK, null);
        assertEquals(6, dbInfo.size());
        assertEquals(0, dbInfo.get("doc_count"));
        assertEquals(0, dbInfo.get("update_seq"));
        assertTrue((Integer) dbInfo.get("disk_size") > 8000);
        assertEquals("database", dbInfo.get("db_name"));
        // following line of test is problematic. Because of System.currentTimeMillis()??
        // assertTrue(System.currentTimeMillis() * 1000 > (Long) dbInfo.get("instance_start_time"));
        assertTrue(dbInfo.containsKey("db_uuid"));

        send("PUT", "/database", Status.DUPLICATE, null);
        send("PUT", "/database2", Status.CREATED, null);

        List<String> allDbs = new ArrayList<String>();
        allDbs.add("cblite-test");
        allDbs.add("database");
        allDbs.add("database2");
        send("GET", "/_all_dbs", Status.OK, allDbs);
        dbInfo = (Map<String, Object>) send("GET", "/database2", Status.OK, null);
        assertEquals("database2", dbInfo.get("db_name"));

        send("DELETE", "/database2", Status.OK, null);
        allDbs.remove("database2");
        send("GET", "/_all_dbs", Status.OK, allDbs);

        send("PUT", "/database%2Fwith%2Fslashes", Status.CREATED, null);
        dbInfo = (Map<String, Object>) send("GET", "/database%2Fwith%2Fslashes", Status.OK, null);
        assertEquals("database/with/slashes", dbInfo.get("db_name"));
    }

    public void testDocWithAttachment() throws IOException {

        String inlineTextString = "Inline text string created by cblite functional test";

        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> attachment = new HashMap<String, Object>();
        attachment.put("content_type", "text/plain");
        attachment.put("data", "SW5saW5lIHRleHQgc3RyaW5nIGNyZWF0ZWQgYnkgY2JsaXRlIGZ1bmN0aW9uYWwgdGVzdA==");

        Map<String, Object> attachments = new HashMap<String, Object>();
        attachments.put("inline.txt", attachment);

        Map<String, Object> docWithAttachment = new HashMap<String, Object>();
        docWithAttachment.put("_id", "docWithAttachment");
        docWithAttachment.put("text", inlineTextString);
        docWithAttachment.put("_attachments", attachments);

        Map<String, Object> result = (Map<String, Object>) sendBody("PUT", "/db/docWithAttachment", docWithAttachment, Status.CREATED, null);

        Map expChanges = new HashMap<String, Map<String, Object>>();
        List changesResults = new ArrayList();
        Map docChanges = new HashMap<String, Object>();
        docChanges.put("id", "docWithAttachment");
        docChanges.put("seq", 1);
        List lChanges = new ArrayList<Map<String, Object>>();
        HashMap mChanges = new HashMap<String, Object>();
        mChanges.put("rev", result.get("rev"));
        lChanges.add(mChanges);
        docChanges.put("changes", lChanges);
        changesResults.add(docChanges);
        expChanges.put("results", changesResults);
        expChanges.put("last_seq", 1);
        send("GET", "/db/_changes?feed=normal&heartbeat=300000&style=all_docs", Status.OK, expChanges);

        result = (Map<String, Object>) send("GET", "/db/docWithAttachment", Status.OK, null);
        Map<String, Object> attachmentsResult = (Map<String, Object>) result.get("_attachments");
        Map<String, Object> attachmentResult = (Map<String, Object>) attachmentsResult.get("inline.txt");

        // there should be either a content_type or content-type field.
        //https://github.com/couchbase/couchbase-lite-android-core/issues/12
        //content_type becomes null for attachments in responses, should be as set in Content-Type
        String contentTypeField = (String) attachmentResult.get("content_type");
        assertTrue(attachmentResult.containsKey("content_type"));
        assertNotNull(contentTypeField);

        // no Accept
        URLConnection conn = sendRequest("GET", "/db/docWithAttachment/inline.txt", null, null);
        String contentType = conn.getHeaderField("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("text/plain"));
        StringWriter writer = new StringWriter();
        InputStream is = conn.getInputStream();
        IOUtils.copy(is, writer, "UTF-8");
        is.close();
        String responseString = writer.toString();
        assertTrue(responseString.contains(inlineTextString));
        writer.close();

        // With Accept: text/plain
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/plain");
        conn = sendRequest("GET", "/db/docWithAttachment/inline.txt", headers, null);
        contentType = conn.getHeaderField("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("text/plain"));
        writer = new StringWriter();
        is = conn.getInputStream();
        IOUtils.copy(is, writer, "UTF-8");
        is.close();
        responseString = writer.toString();
        assertTrue(responseString.contains(inlineTextString));
        writer.close();

        // With Accept: application/json
        headers.put("Accept", "application/json");
        conn = sendRequest("GET", "/db/docWithAttachment/inline.txt", headers, null);
        assertEquals(Status.NOT_ACCEPTABLE, conn.getResponseCode());
        is = conn.getInputStream();
        Map<String, Object> body = Manager.getObjectMapper().readValue(is, Map.class);
        is.close();
        assertEquals(406, body.get("status"));
        assertEquals("not_acceptable", body.get("error"));

        // with attachments=true query parameter
        result = (Map<String, Object>)send("GET", "/db/docWithAttachment?attachments=true", Status.OK, null);
        assertNotNull(result);
        assertNotNull(result.get("_attachments"));
        Map<String, Object> att = (Map<String, Object>) ((Map<String, Object>) result.get("_attachments")).get("inline.txt");
        assertNotNull(att);
        assertNotNull(att.get("data"));
        assertTrue(((String)att.get("data")).length() > 0);
        assertFalse(att.containsKey("stub"));

        // without attachments=true query parameter
        result = (Map<String, Object>)send("GET", "/db/docWithAttachment", Status.OK, null);
        assertNotNull(result);
        assertNotNull(result.get("_attachments"));
        att = (Map<String, Object>) ((Map<String, Object>) result.get("_attachments")).get("inline.txt");
        assertNotNull(att);
        assertNotNull(att.get("stub"));
        assertFalse(att.containsKey("data"));
    }

    private Map<String, Object> valueMapWithRev(String revId) {
        Map<String, Object> value = valueMapWithRevNoConflictArray(revId);
        value.put("_conflicts", new ArrayList<String>());
        return value;
    }

    private Map<String, Object> valueMapWithRevNoConflictArray(String revId) {
        Map<String, Object> value = new HashMap<String, Object>();
        value.put("rev", revId);
        return value;
    }

    // - (void) test_Docs in Router_Tests.m
    public void testDocs() {
        send("PUT", "/db", Status.CREATED, null);

        // PUT:
        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("message", "hello");
        Map<String, Object> result = (Map<String, Object>) sendBody("PUT", "/db/doc1", doc1, Status.CREATED, null);
        String revID = (String) result.get("rev");
        assertTrue(revID.startsWith("1-"));

        // PUT to update:
        doc1.put("message", "goodbye");
        doc1.put("_rev", revID);
        result = (Map<String, Object>) sendBody("PUT", "/db/doc1", doc1, Status.CREATED, null);
        Log.v(TAG, "PUT returned %s", result);
        revID = (String) result.get("rev");
        assertTrue(revID.startsWith("2-"));

        doc1.put("_id", "doc1");
        doc1.put("_rev", revID);
        result = (Map<String, Object>) send("GET", "/db/doc1", Status.OK, doc1);

        // Add more docs:
        Map<String, Object> docX = new HashMap<String, Object>();
        docX.put("message", "hello");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc3", docX, Status.CREATED, null);
        String revID3 = (String) result.get("rev");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc2", docX, Status.CREATED, null);
        String revID2 = (String) result.get("rev");

        // _all_docs:
        result = (Map<String, Object>) send("GET", "/db/_all_docs", Status.OK, null);
        assertEquals(3, result.get("total_rows"));
        assertEquals(0, result.get("offset"));

        Map<String, Object> value1 = valueMapWithRev(revID);
        Map<String, Object> value2 = valueMapWithRev(revID2);
        Map<String, Object> value3 = valueMapWithRev(revID3);

        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("id", "doc1");
        row1.put("key", "doc1");
        row1.put("value", value1);
        row1.put("doc", null);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("id", "doc2");
        row2.put("key", "doc2");
        row2.put("value", value2);
        row2.put("doc", null);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("id", "doc3");
        row3.put("key", "doc3");
        row3.put("value", value3);
        row3.put("doc", null);

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        expectedRows.add(row1);
        expectedRows.add(row2);
        expectedRows.add(row3);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(expectedRows, rows);

        // DELETE:
        result = (Map<String, Object>) send("DELETE", String.format("/db/doc1?rev=%s", revID), Status.OK, null);
        revID = (String) result.get("rev");
        assertTrue(revID.startsWith("3-"));

        send("GET", "/db/doc1", Status.NOT_FOUND, null);

        // _changes:
        List<Object> changes1 = new ArrayList<Object>();
        changes1.add(valueMapWithRevNoConflictArray(revID));
        List<Object> changes2 = new ArrayList<Object>();
        changes2.add(valueMapWithRevNoConflictArray(revID2));
        List<Object> changes3 = new ArrayList<Object>();
        changes3.add(valueMapWithRevNoConflictArray(revID3));

        Map<String, Object> result1 = new HashMap<String, Object>();
        result1.put("id", "doc1");
        result1.put("seq", 5);
        result1.put("deleted", true);
        result1.put("changes", changes1);
        Map<String, Object> result2 = new HashMap<String, Object>();
        result2.put("id", "doc2");
        result2.put("seq", 4);
        result2.put("changes", changes2);
        Map<String, Object> result3 = new HashMap<String, Object>();
        result3.put("id", "doc3");
        result3.put("seq", 3);
        result3.put("changes", changes3);

        List<Object> results = new ArrayList<Object>();
        results.add(result3);
        results.add(result2);
        results.add(result1);

        Map<String, Object> expectedChanges = new HashMap<String, Object>();
        expectedChanges.put("last_seq", 5);
        expectedChanges.put("results", results);

        send("GET", "/db/_changes", Status.OK, expectedChanges);

        // _changes with ?since:
        results.remove(result3);
        results.remove(result2);
        expectedChanges.put("results", results);
        send("GET", "/db/_changes?since=4", Status.OK, expectedChanges);

        Map<String, Object> body = new HashMap<>();
        body.put("since", 4);
        sendBody("POST", "/db/_changes", body, Status.OK, expectedChanges);

        Map<String, Object> expectedBody = new HashMap<String, Object>();
        expectedBody.put("error", "method_not_allowed");
        expectedBody.put("status", 405);
        // 405 - PUT /{db}/_changes is not supported
        sendBody("PUT", "/db/_changes", body, Status.METHOD_NOT_ALLOWED, expectedBody);
        // 405 - DELETE /{db}/_changes is not supported
        send("DELETE", "/db/_changes", Status.METHOD_NOT_ALLOWED, expectedBody);

        results.remove(result1);
        expectedChanges.put("results", results);
        send("GET", "/db/_changes?since=5", Status.OK, expectedChanges);

        body.put("since", 5);
        sendBody("POST", "/db/_changes", body, Status.OK, expectedChanges);

        // Put with _deleted to delete a doc:
        Log.d(TAG, "Put with _deleted to delete a doc");
        send("GET", "/db/doc5", Status.NOT_FOUND, null);
        Map<String, Object> doc5 = new HashMap<String, Object>();
        doc5.put("message", "hello5");
        Map<String, Object> resultDoc5 = (Map<String, Object>) sendBody("PUT", "/db/doc5", doc5, Status.CREATED, null);
        String revIdDoc5 = (String) resultDoc5.get("rev");
        assertTrue(revIdDoc5.startsWith("1-"));
        doc5.put("_deleted", true);
        doc5.put("_rev", revIdDoc5);
        doc5.put("_id", "doc5");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc5", doc5, Status.OK, null);
        send("GET", "/db/doc5", Status.NOT_FOUND, null);
        Log.d(TAG, "Finished put with _deleted to delete a doc");
    }

    // - (void) test_LocalDocs in Router_Tests.m
    public void testLocalDocs() {
        send("PUT", "/db", Status.CREATED, null);

        // PUT a local doc:
        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("message", "hello");
        Map<String, Object> result = (Map<String, Object>) sendBody("PUT", "/db/_local/doc1", doc1, Status.CREATED, null);
        String revID = (String) result.get("rev");
        assertTrue(revID.startsWith("1-"));

        // GET it:
        doc1.put("_id", "_local/doc1");
        doc1.put("_rev", revID);
        result = (Map<String, Object>) send("GET", "/db/_local/doc1", Status.OK, doc1);

        // Local doc should not appear in _changes feed:
        Map<String, Object> expectedChanges = new HashMap<String, Object>();
        expectedChanges.put("last_seq", 0);
        expectedChanges.put("results", new ArrayList<Object>());
        send("GET", "/db/_changes", Status.OK, expectedChanges);

        sendBody("POST", "/db/_changes", new HashMap<>(), Status.OK, expectedChanges);
    }

    // - (void) test_AllDocs in Router_Tests.m
    public void testAllDocs() {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> result;
        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("message", "hello");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc1", doc1, Status.CREATED, null);
        String revID = (String) result.get("rev");
        Map<String, Object> doc3 = new HashMap<String, Object>();
        doc3.put("message", "bonjour");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc3", doc3, Status.CREATED, null);
        String revID3 = (String) result.get("rev");
        Map<String, Object> doc2 = new HashMap<String, Object>();
        doc2.put("message", "guten tag");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc2", doc2, Status.CREATED, null);
        String revID2 = (String) result.get("rev");

        // _all_docs:
        result = (Map<String, Object>) send("GET", "/db/_all_docs", Status.OK, null);
        assertEquals(3, result.get("total_rows"));
        assertEquals(0, result.get("offset"));

        Map<String, Object> value1 = valueMapWithRev(revID);
        Map<String, Object> value2 = valueMapWithRev(revID2);
        Map<String, Object> value3 = valueMapWithRev(revID3);


        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("id", "doc1");
        row1.put("key", "doc1");
        row1.put("value", value1);
        row1.put("doc", null);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("id", "doc2");
        row2.put("key", "doc2");
        row2.put("value", value2);
        row2.put("doc", null);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("id", "doc3");
        row3.put("key", "doc3");
        row3.put("value", value3);
        row3.put("doc", null);

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        expectedRows.add(row1);
        expectedRows.add(row2);
        expectedRows.add(row3);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(expectedRows, rows);

        // ?include_docs:
        result = (Map<String, Object>) send("GET", "/db/_all_docs?include_docs=true", Status.OK, null);
        assertEquals(3, result.get("total_rows"));
        assertEquals(0, result.get("offset"));

        doc1.put("_id", "doc1");
        doc1.put("_rev", revID);
        row1.put("doc", doc1);

        doc2.put("_id", "doc2");
        doc2.put("_rev", revID2);
        row2.put("doc", doc2);

        doc3.put("_id", "doc3");
        doc3.put("_rev", revID3);
        row3.put("doc", doc3);

        List<Map<String, Object>> expectedRowsWithDocs = new ArrayList<Map<String, Object>>();
        expectedRowsWithDocs.add(row1);
        expectedRowsWithDocs.add(row2);
        expectedRowsWithDocs.add(row3);

        rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(expectedRowsWithDocs, rows);
    }

    // - (void) test_Views in Router_Tests.m
    public void testViews() throws CouchbaseLiteException {
        send("PUT", "/db", Status.CREATED, null);

        // PUT:
        Map<String, Object> result;
        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("message", "hello");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc1", doc1, Status.CREATED, null);
        String revID = (String) result.get("rev");
        Map<String, Object> doc3 = new HashMap<String, Object>();
        doc3.put("message", "bonjour");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc3", doc3, Status.CREATED, null);
        String revID3 = (String) result.get("rev");
        Map<String, Object> doc2 = new HashMap<String, Object>();
        doc2.put("message", "guten tag");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc2", doc2, Status.CREATED, null);
        String revID2 = (String) result.get("rev");

        Database db = manager.getDatabase("db");
        View view = db.getView("design/view");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("message"), null);
            }
        }, null, "1");

        // Build up our expected result
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("id", "doc1");
        row1.put("key", "hello");
        row1.put("doc", null);
        row1.put("value", null);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("id", "doc2");
        row2.put("key", "guten tag");
        row2.put("doc", null);
        row2.put("value", null);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("id", "doc3");
        row3.put("key", "bonjour");
        row3.put("doc", null);
        row3.put("value", null);


        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        expectedRows.add(row3);
        expectedRows.add(row2);
        expectedRows.add(row1);

        Map<String, Object> expectedResult = new HashMap<String, Object>();
        expectedResult.put("offset", 0);
        expectedResult.put("total_rows", 3);
        expectedResult.put("rows", expectedRows);

        // Query the view and check the result:
        send("GET", "/db/_design/design/_view/view", Status.OK, expectedResult);

        // Check the ETag:
        URLConnection conn = sendRequest("GET", "/db/_design/design/_view/view", null, null);
        String etag = conn.getHeaderField("Etag");
        assertEquals(String.format("\"%d\"", view.getLastSequenceIndexed()), etag);

        // Try a conditional GET:
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("If-None-Match", etag);
        conn = sendRequest("GET", "/db/_design/design/_view/view", headers, null);
        assertEquals(Status.NOT_MODIFIED, conn.getResponseCode());

        // Update the database:
        Map<String, Object> doc4 = new HashMap<String, Object>();
        doc4.put("message", "aloha");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc4", doc4, Status.CREATED, null);

        // Try a conditional GET:
        conn = sendRequest("GET", "/db/_design/design/_view/view", headers, null);
        assertEquals(Status.OK, conn.getResponseCode());
        result = (Map<String, Object>) parseJSONResponse(conn);
        assertEquals(4, result.get("total_rows"));
    }

    public void testPostBulkDocs() {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> bulk_doc1 = new HashMap<String, Object>();
        bulk_doc1.put("_id", "bulk_message1");
        bulk_doc1.put("baz", "hello");

        Map<String, Object> bulk_doc2 = new HashMap<String, Object>();
        bulk_doc2.put("_id", "bulk_message2");
        bulk_doc2.put("baz", "hi");

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(bulk_doc1);
        list.add(bulk_doc2);

        Map<String, Object> bodyObj = new HashMap<String, Object>();
        bodyObj.put("docs", list);

        List<Map<String, Object>> bulk_result =
                (ArrayList<Map<String, Object>>) sendBody("POST", "/db/_bulk_docs", bodyObj, Status.CREATED, null);

        assertEquals(2, bulk_result.size());
        assertEquals(bulk_result.get(0).get("id"), bulk_doc1.get("_id"));
        assertNotNull(bulk_result.get(0).get("rev"));
        assertEquals(bulk_result.get(1).get("id"), bulk_doc2.get("_id"));
        assertNotNull(bulk_result.get(1).get("rev"));
    }


    public void failingTestPostBulkDocsWithConflict() {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> bulk_doc1 = new HashMap<String, Object>();
        bulk_doc1.put("_id", "bulk_message1");
        bulk_doc1.put("baz", "hello");

        Map<String, Object> bulk_doc2 = new HashMap<String, Object>();
        bulk_doc2.put("_id", "bulk_message2");
        bulk_doc2.put("baz", "hi");


        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        list.add(bulk_doc1);
        list.add(bulk_doc2);
        list.add(bulk_doc2);

        Map<String, Object> bodyObj = new HashMap<String, Object>();
        bodyObj.put("docs", list);

        List<Map<String, Object>> bulk_result =
                (ArrayList<Map<String, Object>>) sendBody("POST", "/db/_bulk_docs", bodyObj, Status.CREATED, null);

        assertEquals(3, bulk_result.size());
        assertEquals(bulk_result.get(0).get("id"), bulk_doc1.get("_id"));
        assertNotNull(bulk_result.get(0).get("rev"));
        assertEquals(bulk_result.get(1).get("id"), bulk_doc2.get("_id"));
        assertNotNull(bulk_result.get(1).get("rev"));
        assertEquals(2, bulk_result.get(2).size());
        assertEquals(bulk_result.get(2).get("id"), bulk_doc2.get("_id"));
        assertEquals(bulk_result.get(2).get("error"), "conflict");


        list = new ArrayList<Map<String, Object>>();
        list.add(bulk_doc1);

        bodyObj = new HashMap<String, Object>();
        bodyObj.put("docs", list);

        bulk_result = (ArrayList<Map<String, Object>>) sendBody("POST", "/db/_bulk_docs", bodyObj, Status.CREATED, null);
        //https://github.com/couchbase/couchbase-lite-android/issues/79
        assertEquals(1, bulk_result.size());
        assertEquals(2, bulk_result.get(0).size());
        assertEquals(bulk_result.get(0).get("id"), bulk_doc1.get("_id"));
        assertEquals(bulk_result.get(0).get("error"), "conflict");
    }


    public void testPostKeysView() throws CouchbaseLiteException {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> result;

        Database db = manager.getDatabase("db");
        View view = db.getView("design/view");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("message"), null);
            }
        }, null, "1");

        Map<String, Object> key_doc1 = new HashMap<String, Object>();
        key_doc1.put("parentId", "12345");
        result = (Map<String, Object>) sendBody("PUT", "/db/key_doc1", key_doc1, Status.CREATED, null);
        view = db.getView("design/view");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get("parentId").equals("12345")) {
                    emitter.emit(document.get("parentId"), document);
                }
            }
        }, null, "1");

        List<Object> keys = new ArrayList<Object>();
        keys.add("12345");
        Map<String, Object> bodyObj = new HashMap<String, Object>();
        bodyObj.put("keys", keys);
        URLConnection conn = sendRequest("POST", "/db/_design/design/_view/view", null, bodyObj);
        result = (Map<String, Object>) parseJSONResponse(conn);
        assertEquals(1, result.get("total_rows"));
    }

    public void testRevsDiff() {

        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> doc = new HashMap<String, Object>();
        Map<String, Object> doc1r1 = (Map<String, Object>) sendBody("PUT", "/db/11111", doc, Status.CREATED, null);
        String doc1r1ID = (String) doc1r1.get("rev");

        Map<String, Object> doc2r1 = (Map<String, Object>) sendBody("PUT", "/db/22222", doc, Status.CREATED, null);
        String doc2r1ID = (String) doc2r1.get("rev");

        Map<String, Object> doc3r1 = (Map<String, Object>) sendBody("PUT", "/db/33333", doc, Status.CREATED, null);
        String doc3r1ID = (String) doc3r1.get("rev");


        Map<String, Object> doc1v2 = new HashMap<String, Object>();
        doc1v2.put("_rev", doc1r1ID);
        Map<String, Object> doc1r2 = (Map<String, Object>) sendBody("PUT", "/db/11111", doc1v2, Status.CREATED, null);
        String doc1r2ID = (String) doc1r2.get("rev");

        Map<String, Object> doc2v2 = new HashMap<String, Object>();
        doc2v2.put("_rev", doc2r1ID);
        sendBody("PUT", "/db/22222", doc2v2, Status.CREATED, null);

        Map<String, Object> doc1v3 = new HashMap<String, Object>();
        doc1v3.put("_rev", doc1r2ID);
        Map<String, Object> doc1r3 = (Map<String, Object>) sendBody("PUT", "/db/11111", doc1v3, Status.CREATED, null);
        String doc1r3ID = (String) doc1r1.get("rev");

        //now build up the request
        List<String> doc1Revs = new ArrayList<String>();
        doc1Revs.add(doc1r2ID);
        doc1Revs.add("3-1000");

        List<String> doc2Revs = new ArrayList<String>();
        doc2Revs.add(doc2r1ID);

        List<String> doc3Revs = new ArrayList<String>();
        doc3Revs.add("10-1000");

        List<String> doc9Revs = new ArrayList<String>();
        doc9Revs.add("6-1000");

        Map<String, Object> revsDiffRequest = new HashMap<String, Object>();
        revsDiffRequest.put("11111", doc1Revs);
        revsDiffRequest.put("22222", doc2Revs);
        revsDiffRequest.put("33333", doc3Revs);
        revsDiffRequest.put("99999", doc9Revs);

        //now build up the expected response
        List<String> doc1missing = new ArrayList<String>();
        doc1missing.add("3-1000");

        List<String> doc3missing = new ArrayList<String>();
        doc3missing.add("10-1000");

        List<String> doc9missing = new ArrayList<String>();
        doc9missing.add("6-1000");

        Map<String, Object> doc1missingMap = new HashMap<String, Object>();
        doc1missingMap.put("missing", doc1missing);

        Map<String, Object> doc3missingMap = new HashMap<String, Object>();
        doc3missingMap.put("missing", doc3missing);

        Map<String, Object> doc9missingMap = new HashMap<String, Object>();
        doc9missingMap.put("missing", doc9missing);

        Map<String, Object> revsDiffResponse = new HashMap<String, Object>();
        revsDiffResponse.put("11111", doc1missingMap);
        revsDiffResponse.put("33333", doc3missingMap);
        revsDiffResponse.put("99999", doc9missingMap);

        sendBody("POST", "/db/_revs_diff", revsDiffRequest, Status.OK, revsDiffResponse);
    }

    public void testFacebookToken() throws Exception {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("email", "foo@bar.com");
        doc1.put("remote_url", getReplicationURL().toExternalForm());
        doc1.put("access_token", "fake_access_token");

        Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_facebook_token", doc1, Status.OK, null);
        Log.v(TAG, "result %s", result);
    }

    public void testPersonaAssertion() {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> doc1 = new HashMap<String, Object>();
        String sampleAssertion = "eyJhbGciOiJSUzI1NiJ9.eyJwdWJsaWMta2V5Ijp7ImFsZ29yaXRobSI6IkRTIiwieSI6ImNhNWJiYTYzZmI4MDQ2OGE0MjFjZjgxYTIzN2VlMDcwYTJlOTM4NTY0ODhiYTYzNTM0ZTU4NzJjZjllMGUwMDk0ZWQ2NDBlOGNhYmEwMjNkYjc5ODU3YjkxMzBlZGNmZGZiNmJiNTUwMWNjNTk3MTI1Y2NiMWQ1ZWQzOTVjZTMyNThlYjEwN2FjZTM1ODRiOWIwN2I4MWU5MDQ4NzhhYzBhMjFlOWZkYmRjYzNhNzNjOTg3MDAwYjk4YWUwMmZmMDQ4ODFiZDNiOTBmNzllYzVlNDU1YzliZjM3NzFkYjEzMTcxYjNkMTA2ZjM1ZDQyZmZmZjQ2ZWZiZDcwNjgyNWQiLCJwIjoiZmY2MDA0ODNkYjZhYmZjNWI0NWVhYjc4NTk0YjM1MzNkNTUwZDlmMWJmMmE5OTJhN2E4ZGFhNmRjMzRmODA0NWFkNGU2ZTBjNDI5ZDMzNGVlZWFhZWZkN2UyM2Q0ODEwYmUwMGU0Y2MxNDkyY2JhMzI1YmE4MWZmMmQ1YTViMzA1YThkMTdlYjNiZjRhMDZhMzQ5ZDM5MmUwMGQzMjk3NDRhNTE3OTM4MDM0NGU4MmExOGM0NzkzMzQzOGY4OTFlMjJhZWVmODEyZDY5YzhmNzVlMzI2Y2I3MGVhMDAwYzNmNzc2ZGZkYmQ2MDQ2MzhjMmVmNzE3ZmMyNmQwMmUxNyIsInEiOiJlMjFlMDRmOTExZDFlZDc5OTEwMDhlY2FhYjNiZjc3NTk4NDMwOWMzIiwiZyI6ImM1MmE0YTBmZjNiN2U2MWZkZjE4NjdjZTg0MTM4MzY5YTYxNTRmNGFmYTkyOTY2ZTNjODI3ZTI1Y2ZhNmNmNTA4YjkwZTVkZTQxOWUxMzM3ZTA3YTJlOWUyYTNjZDVkZWE3MDRkMTc1ZjhlYmY2YWYzOTdkNjllMTEwYjk2YWZiMTdjN2EwMzI1OTMyOWU0ODI5YjBkMDNiYmM3ODk2YjE1YjRhZGU1M2UxMzA4NThjYzM0ZDk2MjY5YWE4OTA0MWY0MDkxMzZjNzI0MmEzODg5NWM5ZDViY2NhZDRmMzg5YWYxZDdhNGJkMTM5OGJkMDcyZGZmYTg5NjIzMzM5N2EifSwicHJpbmNpcGFsIjp7ImVtYWlsIjoiamVuc0Btb29zZXlhcmQuY29tIn0sImlhdCI6MTM1ODI5NjIzNzU3NywiZXhwIjoxMzU4MzgyNjM3NTc3LCJpc3MiOiJsb2dpbi5wZXJzb25hLm9yZyJ9.RnDK118nqL2wzpLCVRzw1MI4IThgeWpul9jPl6ypyyxRMMTurlJbjFfs-BXoPaOem878G8-4D2eGWS6wd307k7xlPysevYPogfFWxK_eDHwkTq3Ts91qEDqrdV_JtgULC8c1LvX65E0TwW_GL_TM94g3CvqoQnGVxxoaMVye4ggvR7eOZjimWMzUuu4Lo9Z-VBHBj7XM0UMBie57CpGwH4_Wkv0V_LHZRRHKdnl9ISp_aGwfBObTcHG9v0P3BW9vRrCjihIn0SqOJQ9obl52rMf84GD4Lcy9NIktzfyka70xR9Sh7ALotW7rWywsTzMTu3t8AzMz2MJgGjvQmx49QA~eyJhbGciOiJEUzEyOCJ9.eyJleHAiOjEzNTgyOTY0Mzg0OTUsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDk4NC8ifQ.4FV2TrUQffDya0MOxOQlzJQbDNvCPF2sfTIJN7KOLvvlSFPknuIo5g";
        doc1.put("assertion", sampleAssertion);

        Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_persona_assertion", doc1, Status.OK, null);
        Log.v(TAG, "result %s", result);
        String email = (String) result.get("email");
        assertEquals(email, "jens@mooseyard.com");
    }

    public void testPushReplicate() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {

            // fake checkpoint response 404
            MockCheckpointGet mockCheckpointGet = new MockCheckpointGet();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointGet);

            server.play();

            Map<String, Object> replicateJsonMap = getPushReplicationParsedJson(server.getUrl("/db"));

            Log.v(TAG, "map: " + replicateJsonMap);
            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.v(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));

            boolean success = waitForReplicationToFinish();
            assertTrue(success);
        }finally {
            server.shutdown();
        }
    }

    private boolean waitForReplicationToFinish() throws InterruptedException {
        int maxTimeToWaitMs = 30 * 1000;
        int timeWaited = 0;
        boolean success = true;

        ArrayList<Object> activeTasks = (ArrayList<Object>) send("GET", "/_active_tasks", Status.OK, null);
        while (activeTasks.size() > 0 && timeWaited < maxTimeToWaitMs) {
            int timeToWait = 200;
            Thread.sleep(timeToWait);
            activeTasks = (ArrayList<Object>) send("GET", "/_active_tasks", Status.OK, null);
            timeWaited += timeToWait;
        }

        if (timeWaited >= maxTimeToWaitMs) {
            success = false;
        }
        return success;
    }

    public void testPullReplicate() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // kick off replication via REST api
            Map<String, Object> replicateJsonMap = getPullReplicationParsedJson(server.getUrl("/db"));
            Log.v(TAG, "map: " + replicateJsonMap);
            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.v(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));

            // wait for replication to finish
            boolean success = waitForReplicationToFinish();
            assertTrue(success);
        }finally {
            // cleanup
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/106
     */
    public void testResolveConflict() throws Exception {

        Map<String, Object> result;

        // Create a conflict on purpose
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        SavedRevision winningRev = null;
        SavedRevision losingRev = null;
        if (doc.getCurrentRevisionId().equals(rev2a.getId())) {
            winningRev = rev2a;
            losingRev = rev2b;
        } else {
            winningRev = rev2b;
            losingRev = rev2a;
        }

        assertEquals(2, doc.getConflictingRevisions().size());
        assertEquals(2, doc.getLeafRevisions().size());

        result = (Map<String, Object>) send("GET", String.format("/%s/%s?conflicts=true", DEFAULT_TEST_DB, doc.getId()), Status.OK, null);
        List<String> conflicts = (List) result.get("_conflicts");
        assertEquals(1, conflicts.size());
        String conflictingRevId = conflicts.get(0);
        assertEquals(losingRev.getId(), conflictingRevId);

        assertNotNull(database.getDocument(doc.getId()));

        result = (Map<String, Object>) send("DELETE", String.format("/%s/%s?rev=%s", DEFAULT_TEST_DB, doc.getId(), conflictingRevId), Status.OK, null);

        result = (Map<String, Object>) send("GET", String.format("/%s/%s?conflicts=true", DEFAULT_TEST_DB, doc.getId()), Status.OK, null);

        conflicts = (List) result.get("_conflicts");
        assertEquals(0, conflicts.size());
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/293
     */
    public void testTotalRowsAttributeOnViewQuery() throws CouchbaseLiteException {
        send("PUT", "/db", Status.CREATED, null);

        // PUT:
        Map<String, Object> result;
        Map<String, Object> doc1 = new HashMap<String, Object>();
        doc1.put("message", "hello");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc1", doc1, Status.CREATED, null);
        String revID = (String) result.get("rev");
        Map<String, Object> doc3 = new HashMap<String, Object>();
        doc3.put("message", "bonjour");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc3", doc3, Status.CREATED, null);
        String revID3 = (String) result.get("rev");
        Map<String, Object> doc2 = new HashMap<String, Object>();
        doc2.put("message", "guten tag");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc2", doc2, Status.CREATED, null);
        String revID2 = (String) result.get("rev");

        Database db = manager.getDatabase("db");
        View view = db.getView("design/view");
        view.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                emitter.emit(document.get("message"), null);
            }
        }, null, "1");

        // Build up our expected result
        Map<String, Object> row1 = new HashMap<String, Object>();
        row1.put("id", "doc1");
        row1.put("key", "hello");
        row1.put("doc", null);
        row1.put("value", null);
        Map<String, Object> row2 = new HashMap<String, Object>();
        row2.put("id", "doc2");
        row2.put("key", "guten tag");
        row2.put("doc", null);
        row2.put("value", null);
        Map<String, Object> row3 = new HashMap<String, Object>();
        row3.put("id", "doc3");
        row3.put("key", "bonjour");
        row3.put("doc", null);
        row3.put("value", null);

        List<Map<String, Object>> expectedRows = new ArrayList<Map<String, Object>>();
        expectedRows.add(row3);
        expectedRows.add(row2);
        //expectedRows.add(row1);

        Map<String, Object> expectedResult = new HashMap<String, Object>();
        expectedResult.put("offset", 0);
        expectedResult.put("total_rows", 3);
        expectedResult.put("rows", expectedRows);

        // Query the view and check the result:
        send("GET", "/db/_design/design/_view/view?limit=2", Status.OK, expectedResult);

        // Check the ETag:
        URLConnection conn = sendRequest("GET", "/db/_design/design/_view/view", null, null);
        String etag = conn.getHeaderField("Etag");
        assertEquals(String.format("\"%d\"", view.getLastSequenceIndexed()), etag);

        // Try a conditional GET:
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("If-None-Match", etag);
        conn = sendRequest("GET", "/db/_design/design/_view/view", headers, null);
        assertEquals(Status.NOT_MODIFIED, conn.getResponseCode());

        // Update the database:
        Map<String, Object> doc4 = new HashMap<String, Object>();
        doc4.put("message", "aloha");
        result = (Map<String, Object>) sendBody("PUT", "/db/doc4", doc4, Status.CREATED, null);

        // Try a conditional GET:
        conn = sendRequest("GET", "/db/_design/design/_view/view?limit=2", headers, null);
        assertEquals(Status.OK, conn.getResponseCode());
        result = (Map<String, Object>) parseJSONResponse(conn);
        assertEquals(2, ((List) result.get("rows")).size());
        assertEquals(4, result.get("total_rows"));
    }

    public void testSession() {
        send("PUT", "/db", Status.CREATED, null);

        Map<String, Object> session = new HashMap<String, Object>();
        Map<String, Object> userCtx = new HashMap<String, Object>();
        List<String> roles = new ArrayList<String>();
        roles.add("_admin");
        session.put("ok", true);
        userCtx.put("name", null);
        userCtx.put("roles", roles);
        session.put("userCtx", userCtx);

        send("GET", "/_session", Status.OK, session);
        send("GET", "/db/_session", Status.OK, session);
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/291
     */
    public void testCallReplicateTwice() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // kick off 1st replication via REST api
            Map<String, Object> replicateJsonMap = getPullReplicationParsedJson(server.getUrl("/db"));
            Log.i(TAG, "map: " + replicateJsonMap);

            Log.i(TAG, "Call 1st /_replicate");
            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));
            String sessionId1 = (String) result.get("session_id");

            // NOTE: one short replication should be blocked. sendBody() waits till response is ready.
            //      https://github.com/couchbase/couchbase-lite-android/issues/204

            // kick off 2nd replication via REST api
            Log.i(TAG, "Call 2nd /_replicate");
            Map<String, Object> result2 = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result2: " + result2);
            assertNotNull(result2.get("session_id"));
            String sessionId2 = (String) result2.get("session_id");

            // wait for replication to finish
            boolean success = waitForReplicationToFinish();
            assertTrue(success);

            // kick off 3rd replication via REST api
            Log.i(TAG, "Call 3rd /_replicate");
            Map<String, Object> result3 = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result3: " + result3);
            assertNotNull(result3.get("session_id"));
            String sessionId3 = (String) result3.get("session_id");

            // wait for replication to finish
            boolean success3 = waitForReplicationToFinish();
            assertTrue(success3);

            assertFalse(sessionId1.equals(sessionId2));
            assertFalse(sessionId1.equals(sessionId3));
            assertFalse(sessionId2.equals(sessionId3));
        }finally {
            // cleanup
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/291
     */
    public void testCallContinuousReplicateTwice() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // kick off 1st replication via REST api
            Map<String, Object> replicateJsonMap = getPullReplicationParsedJson(server.getUrl("/db"));
            replicateJsonMap.put("continuous", true);
            Log.i(TAG, "map: " + replicateJsonMap);

            Log.i(TAG, "Call 1st /_replicate");
            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));
            String sessionId1 = (String) result.get("session_id");

            // no wait, immediately call new _replicate REST API

            // kick off 2nd replication via REST api => Should be
            Log.i(TAG, "Call 2nd /_replicate");
            Map<String, Object> result2 = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result2: " + result2);
            assertNotNull(result2.get("session_id"));
            String sessionId2 = (String) result2.get("session_id");

            // WAIT replicator becomes IDLE
            List<CountDownLatch> replicationIdleSignals = new ArrayList<CountDownLatch>();
            List<Replication> repls = database.getActiveReplications();
            for(Replication repl : repls){
                if(repl.getStatus()== Replication.ReplicationStatus.REPLICATION_ACTIVE){
                    final CountDownLatch replicationIdleSignal = new CountDownLatch(1);
                    ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdleSignal);
                    repl.addChangeListener(idleObserver);
                    replicationIdleSignals.add(replicationIdleSignal);
                }
            }
            for(CountDownLatch signal : replicationIdleSignals){
                assertTrue(signal.await(30, TimeUnit.SECONDS));
            }

            // kick off 3rd replication via REST api => Should be
            Log.i(TAG, "Call 3rd /_replicate");
            Map<String, Object> result3 = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result3: " + result3);
            assertNotNull(result3.get("session_id"));
            String sessionId3 = (String) result3.get("session_id");

            // WAIT replicator becomes IDLE
            replicationIdleSignals = new ArrayList<CountDownLatch>();
            repls = database.getActiveReplications();
            for(Replication repl : repls){
                if(repl.getStatus()== Replication.ReplicationStatus.REPLICATION_ACTIVE){
                    final CountDownLatch replicationIdleSignal = new CountDownLatch(1);
                    ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(replicationIdleSignal);
                    repl.addChangeListener(idleObserver);
                    replicationIdleSignals.add(replicationIdleSignal);
                }
            }
            for(CountDownLatch signal : replicationIdleSignals){
                assertTrue(signal.await(30, TimeUnit.SECONDS));
            }


            // Cancel Replicator
            replicateJsonMap.put("cancel", true);
            Log.i(TAG, "map: " + replicateJsonMap);
            Map<String, Object> result4 = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result4: " + result4);

            // wait for replication to finish
            boolean success = waitForReplicationToFinish();
            assertTrue(success);

            assertTrue(sessionId1.equals(sessionId2));
            assertTrue(sessionId1.equals(sessionId3));
        }finally {
            // cleanup
            server.shutdown();
        }
    }

    public void testPullReplicateOneShot() throws Exception {

        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();

            // kick off replication via REST api
            Map<String, Object> replicateJsonMap = getPullReplicationParsedJson(server.getUrl("/db"));
            Log.i(TAG, "map: " + replicateJsonMap);

            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));

            ArrayList<Object> activeTasks = (ArrayList<Object>) send("GET", "/_active_tasks", Status.OK, null);
            Log.v(TAG, "activeTasks.size(): " + activeTasks.size());
            for (Object obj : activeTasks) {
                Map<String, Object> resp = (Map<String, Object>) obj;
                assertEquals("Stopped", resp.get("status"));
            }
        }finally {
            // cleanup
            server.shutdown();
        }
    }

    public void testChangesIncludeDocs() throws CouchbaseLiteException {
        // Create a conflict on purpose
        Document doc = database.createDocument();
        SavedRevision rev1 = doc.createRevision().save();
        SavedRevision rev2a = createRevisionWithRandomProps(rev1, false);
        SavedRevision rev2b = createRevisionWithRandomProps(rev1, true);

        SavedRevision winningRev = null;
        SavedRevision losingRev = null;
        if (doc.getCurrentRevisionId().equals(rev2a.getId())) {
            winningRev = rev2a;
            losingRev = rev2b;
        } else {
            winningRev = rev2b;
            losingRev = rev2a;
        }
        assertEquals(2, doc.getConflictingRevisions().size());
        assertEquals(2, doc.getLeafRevisions().size());

        // /{db}/_changes with default parameter -> in changes, one revisions [winning rev]
        String url = String.format("/%s/_changes", DEFAULT_TEST_DB);
        Map<String, Object> res = (Map<String, Object>) send("GET", url, Status.OK, null);
        Log.i(TAG, "[%s] %s", url, res);
        String[] revIDs = obtainChangesRevIDs(res);
        assertEquals(1, revIDs.length);
        assertEquals(winningRev.getId(), revIDs[0]);

        // /{db}/_changes with style=all_docs parameter -> in changes, two revisions [winning rev and losing rev]
        url = String.format("/%s/_changes?style=all_docs", DEFAULT_TEST_DB);
        res = (Map<String, Object>) send("GET", url, Status.OK, null);
        Log.i(TAG, "[%s] %s", url, res);
        revIDs = obtainChangesRevIDs(res);
        assertEquals(2, revIDs.length);
        assertEquals(winningRev.getId(), revIDs[0]);
        assertEquals(losingRev.getId(), revIDs[1]);

        // /{db}/_changes with include_docs=true parameter -> in changes, one revisions [winning rev] and  doc is only winning rev
        url = String.format("/%s/_changes?include_docs=true", DEFAULT_TEST_DB);
        res = (Map<String, Object>) send("GET", url, Status.OK, null);
        Log.i(TAG, "[%s] %s", url, res);
        revIDs = obtainChangesRevIDs(res);
        assertEquals(1, revIDs.length);
        assertEquals(winningRev.getId(), revIDs[0]);

        // /{db}/_changes with include_docs=true & style=all_docs parameters -> in changes, one revisions [winning rev] and doc is only winning rev
        url = String.format("/%s/_changes?include_docs=true&style=all_docs", DEFAULT_TEST_DB);
        res = (Map<String, Object>) send("GET", url, Status.OK, null);
        Log.i(TAG, "[%s] %s", url, res);
        revIDs = obtainChangesRevIDs(res);
        assertEquals(2, revIDs.length);
        assertEquals(winningRev.getId(), revIDs[0]);
        assertEquals(losingRev.getId(), revIDs[1]);

        // NOTE: To test feed=longpoll, use AndroidLiteServ
    }

    private static String[] obtainChangesRevIDs(Map<String, Object> res){
        List<String> revIDs = new ArrayList<String>();
        List<Map<String, Object>> results = (List<Map<String, Object>>)res.get("results");
        Map<String, Object> result = results.get(0);
        List<Map<String, Object>> changes = (List<Map<String, Object>>)result.get("changes");
        for(Map<String, Object> change :changes){
            revIDs.add((String)change.get("rev"));
        }
        return revIDs.toArray(new String[revIDs.size()]);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/660
    // https://github.com/couchbase/couchbase-lite-java-core/issues/191
    public void test_do_DELETE_Attachment() throws IOException {
        String inlineTextString = "Inline text string created by cblite functional test";

        // PUT /{db}
        send("PUT", "/db", Status.CREATED, null);

        // PUT /{db}/{doc}
        Map<String, Object> attachment = new HashMap<String, Object>();
        attachment.put("content_type", "text/plain");
        attachment.put("data", "SW5saW5lIHRleHQgc3RyaW5nIGNyZWF0ZWQgYnkgY2JsaXRlIGZ1bmN0aW9uYWwgdGVzdA==");
        Map<String, Object> attachments = new HashMap<String, Object>();
        attachments.put("inline.txt", attachment);
        Map<String, Object> docWithAttachment = new HashMap<String, Object>();
        docWithAttachment.put("_id", "docWithAttachment");
        docWithAttachment.put("text", inlineTextString);
        docWithAttachment.put("_attachments", attachments);
        Map<String, Object> result = (Map<String, Object>) sendBody("PUT", "/db/docWithAttachment", docWithAttachment, Status.CREATED, null);

        // GET /{db}/{doc}
        result = (Map<String, Object>) send("GET", "/db/docWithAttachment", Status.OK, null);
        Map<String, Object> attachmentsResult = (Map<String, Object>) result.get("_attachments");
        Map<String, Object> attachmentResult = (Map<String, Object>) attachmentsResult.get("inline.txt");
        // there should be either a content_type or content-type field.
        //https://github.com/couchbase/couchbase-lite-android-core/issues/12
        //content_type becomes null for attachments in responses, should be as set in Content-Type
        String contentTypeField = (String) attachmentResult.get("content_type");
        assertTrue(attachmentResult.containsKey("content_type"));
        assertNotNull(contentTypeField);

        String revID = (String) result.get("_rev");
        assertNotNull(revID);

        // GET /{db}/{doc}/{attachment}
        // no Accept
        URLConnection conn = sendRequest("GET", "/db/docWithAttachment/inline.txt", null, null);
        String contentType = conn.getHeaderField("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("text/plain"));
        StringWriter writer = new StringWriter();
        InputStream is = conn.getInputStream();
        IOUtils.copy(is, writer, "UTF-8");
        is.close();
        String responseString = writer.toString();
        assertTrue(responseString.contains(inlineTextString));
        writer.close();

        // DELETE /{db}/{doc}/{attachment} - without rev query parameter.
        send("DELETE", "/db/docWithAttachment/inline.txt", Status.BAD_REQUEST, null);

        // DELETE /{db}/{doc}/{attachment}
        result = (Map<String, Object>) send("DELETE", "/db/docWithAttachment/inline.txt?rev=" + revID, Status.OK, null);
        assertNotNull(result);
        assertEquals("docWithAttachment", (String) result.get("id"));
        assertNotNull(result.get("rev"));
        assertEquals(Boolean.TRUE, (Boolean) result.get("ok"));

        revID = (String) result.get("rev");

        // GET /{db}/{doc}
        result = (Map<String, Object>) send("GET", "/db/docWithAttachment", Status.OK, null);
        assertEquals("docWithAttachment", (String) result.get("_id"));
        assertNotNull(result.get("_rev"));
        assertEquals(revID, (String) result.get("_rev"));
        assertEquals(inlineTextString, (String) result.get("text"));
        assertNull(result.get("_attachments"));

        // GET /{db}/{doc}/{attachment}
        // no Accept
        conn = sendRequest("GET", "/db/docWithAttachment/inline.txt", null, null);
        assertEquals(404, conn.getResponseCode());
        contentType = conn.getHeaderField("Content-Type");
        writer = new StringWriter();
        is = conn.getInputStream();
        IOUtils.copy(is, writer, "UTF-8");
        is.close();
        responseString = writer.toString();
        assertTrue(responseString.contains("404"));
        assertTrue(responseString.contains("not_found"));
        writer.close();
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/476
    public void testReplicateWithDocIDs() throws Exception {
        // create mock sync gateway that will serve as a pull target and return random docs
        int numMockDocsToServe = 0;
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getPreloadedPullTargetMockCouchDB(dispatcher, numMockDocsToServe, 1);
        dispatcher.setServerType(MockDispatcher.ServerType.COUCHDB);
        server.setDispatcher(dispatcher);
        try {
            server.play();
            // kick off replication via REST api
            Map<String, Object> replicateJsonMap = getPullReplicationParsedJson(server.getUrl("/db"));
            List<String> docIDs = new ArrayList();
            docIDs.add("doc0");
            replicateJsonMap.put("doc_ids", docIDs);
            Log.i(TAG, "map: " + replicateJsonMap);
            Map<String, Object> result = (Map<String, Object>) sendBody("POST", "/_replicate", replicateJsonMap, Status.OK, null);
            Log.i(TAG, "result: " + result);
            assertNotNull(result.get("session_id"));

            // Check if /_changes calls includes doc_ids in body.
            RecordedRequest getChangesFeedRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHANGES);
            assertTrue(getChangesFeedRequest.getMethod().equals("POST"));
            String body = getChangesFeedRequest.getUtf8Body();
            Map<String, Object> jsonMap = Manager.getObjectMapper().readValue(body, Map.class);
            assertTrue(jsonMap.containsKey("filter"));
            String filter = (String) jsonMap.get("filter");
            assertEquals("_doc_ids", filter);
            List<String> docids = (List<String>) jsonMap.get("doc_ids");
            assertNotNull(docids);
            assertEquals(1, docids.size());
            assertTrue(docIDs.contains("doc0"));
        }finally {
            server.shutdown();
        }
    }
}
