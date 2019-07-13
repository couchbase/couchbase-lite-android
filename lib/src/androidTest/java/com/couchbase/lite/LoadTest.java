//
// LoadIntegrationTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.couchbase.lite.utils.LoadIntegrationTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class LoadTest extends BaseTest {
    interface VerifyBlock {
        void verify(int n, Result result);
    }

    @Test
    public void testCreate() throws InterruptedException, CouchbaseLiteException {
        long start = System.currentTimeMillis();

        final int n = numIteration();
        final String tag = "Create";
        createDocumentNSave(tag, n);
        verifyByTagName(tag, n);
        assertEquals(n, db.getCount());

        logPerformanceStats("testCreate()", (System.currentTimeMillis() - start));
    }

    @Test
    public void testAddRevisions() {
        final int revs = 1000;
        addRevisions(revs, false);
        addRevisions(revs, true);
    }

    @Test
    @LoadIntegrationTest
    public void testUpdate() throws CouchbaseLiteException {
        long start = System.currentTimeMillis();

        final int n = numIteration();
        final String docID = "doc1";
        String tag = "Create";

        // create doc
        createDocumentNSave(docID, tag);

        Document doc = db.getDocument(docID);
        assertNotNull(doc);
        assertEquals(docID, doc.getId());
        assertEquals(tag, doc.getString("tag"));

        // update doc n times
        tag = "Update";
        updateDoc(doc, n, tag);

        // check document
        doc = db.getDocument(docID);
        assertNotNull(doc);
        assertEquals(docID, doc.getId());
        assertEquals(tag, doc.getString("tag"));
        assertEquals(n, doc.getInt("update"));

        String street = String.format(Locale.ENGLISH, "%d street.", n);
        String phone = String.format(Locale.ENGLISH, "650-000-%04d", n);
        assertEquals(street, doc.getDictionary("address").getString("street"));
        assertEquals(phone, doc.getArray("phones").getString(0));

        logPerformanceStats("testUpdate()", (System.currentTimeMillis() - start));
    }

    @Test
    @LoadIntegrationTest
    public void testRead() throws CouchbaseLiteException {
        long start = System.currentTimeMillis();

        final int n = numIteration();
        final String docID = "doc1";
        final String tag = "Read";

        // create 1 doc
        createDocumentNSave(docID, tag);

        // read the doc n times
        for (int i = 0; i < n; i++) {
            Document doc = db.getDocument(docID);
            assertNotNull(doc);
            assertEquals(docID, doc.getId());
            assertEquals(tag, doc.getString("tag"));
        }

        logPerformanceStats("testRead()", (System.currentTimeMillis() - start));
    }

    @Test
    @LoadIntegrationTest
    public void testDelete() throws CouchbaseLiteException {
        long start = System.currentTimeMillis();

        final int n = numIteration();
        final String tag = "Delete";

        // create & delete doc n times
        for (int i = 0; i < n; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%010d", i);
            createDocumentNSave(docID, tag);
            assertEquals(1, db.getCount());
            Document doc = db.getDocument(docID);
            assertNotNull(doc);
            assertEquals(tag, doc.getString("tag"));
            db.delete(doc);
            assertEquals(0, db.getCount());
        }

        logPerformanceStats("testDelete()", (System.currentTimeMillis() - start));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1447
    @Test
    @LoadIntegrationTest
    public void testGlobalReferenceExcceded() throws InterruptedException, CouchbaseLiteException {
        long start = System.currentTimeMillis();

        // final int n = 20000; // num of docs;
        final int n = numIteration(); // NOTE: changed for unit test
        final int m = 100; // num of fields

        // Without Batch
        for (int i = 0; i < n; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%05d", i));
            for (int j = 0; j < m; j++) {
                doc.setInt(String.valueOf(j), j);
            }
            try {
                db.save(doc);
            }
            catch (CouchbaseLiteException e) {
                log(LogLevel.ERROR, "Failed to save: " + e);
            }
        }

        assertEquals(n, db.getCount());

        logPerformanceStats("testGlobalReferenceExcceded()", (System.currentTimeMillis() - start));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1610
    @Test
    @LoadIntegrationTest
    public void testUpdate2() throws CouchbaseLiteException {
        MutableDocument mDoc = new MutableDocument("doc1");
        Map<String, Object> map = new HashMap<>();
        map.put("ID", "doc1");
        mDoc.setValue("map", map);
        save(mDoc);

        long start = System.currentTimeMillis();

        final int N = 2000;

        for (int i = 0; i < N; i++) {
            map.put("index", i);
            assertTrue(updateMap(map, i, (long) i));
        }

        logPerformanceStats("testUpdate2()", (System.currentTimeMillis() - start));
    }

    private boolean updateMap(Map map, int i, long l) {
        Document doc = db.getDocument(map.get("ID").toString());
        if (doc == null) { return false; }
        MutableDocument newDoc = doc.toMutable();
        newDoc.setValue("map", map);
        newDoc.setInt("int", i);
        newDoc.setLong("long", l);
        try {
            db.save(newDoc);
        }
        catch (CouchbaseLiteException e) {
            log(LogLevel.ERROR, "DB is not responding: " + e);
            return false;
        }
        return true;
    }

    private void addRevisions(final int revisions, final boolean retriveNewDoc) {
        try {
            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    try {
                        MutableDocument mDoc = new MutableDocument("doc");
                        if (retriveNewDoc) { updateDocWithGetDocument(mDoc, revisions); }
                        else { updateDoc(mDoc, revisions); }
                    }
                    catch (CouchbaseLiteException e) {
                        e.printStackTrace();
                    }
                }
            });
            Document doc = db.getDocument("doc");
            assertEquals(revisions - 1, doc.getInt("count")); // start from 0.
        }
        catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    private void updateDoc(MutableDocument doc, final int revisions) throws CouchbaseLiteException {
        for (int i = 0; i < revisions; i++) {
            doc.setValue("count", i);
            db.save(doc);
            System.gc();
        }
    }

    private void updateDocWithGetDocument(MutableDocument doc, final int revisions) throws CouchbaseLiteException {
        for (int i = 0; i < revisions; i++) {
            doc.setValue("count", i);
            db.save(doc);
            doc = db.getDocument("doc").toMutable();
        }
    }

    private void logPerformanceStats(String name, long time) {
        log(LogLevel.INFO, "PerformanceStats: " + name + " -> " + time + " ms");
    }

    private MutableDocument createDocumentWithTag(String id, String tag) {
        MutableDocument doc;
        if (id == null) { doc = new MutableDocument(); }
        else { doc = new MutableDocument(id); }

        // Tag
        doc.setValue("tag", tag);

        // String
        doc.setValue("firstName", "Daniel");
        doc.setValue("lastName", "Tiger");

        // Dictionary:
        MutableDictionary address = new MutableDictionary();
        address.setValue("street", "1 Main street");
        address.setValue("city", "Mountain View");
        address.setValue("state", "CA");
        doc.setValue("address", address);

        // Array:
        MutableArray phones = new MutableArray();
        phones.addValue("650-123-0001");
        phones.addValue("650-123-0002");
        doc.setValue("phones", phones);

        // Date:
        doc.setValue("updated", new Date());

        return doc;
    }

    private void createDocumentNSave(String id, String tag) throws CouchbaseLiteException {
        MutableDocument doc = createDocumentWithTag(id, tag);
        db.save(doc);
    }

    private void createDocumentNSave(String tag, int nDocs) throws CouchbaseLiteException {
        for (int i = 0; i < nDocs; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%010d", i);
            createDocumentNSave(docID, tag);
        }
    }

    private Document updateDoc(Document doc, int rounds, String tag) throws CouchbaseLiteException {
        Document tmpDoc = doc;
        for (int i = 1; i <= rounds; i++) {
            MutableDocument mDoc = tmpDoc.toMutable();
            mDoc.setValue("update", i);

            mDoc.setValue("tag", tag);

            MutableDictionary address = mDoc.getDictionary("address");
            assertNotNull(address);
            String street = String.format(Locale.ENGLISH, "%d street.", i);
            address.setValue("street", street);
            mDoc.setDictionary("address", address);

            MutableArray phones = mDoc.getArray("phones");
            assertNotNull(phones);
            assertEquals(2, phones.count());
            String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
            phones.setValue(0, phone);
            mDoc.setArray("phones", phones);

            mDoc.setValue("updated", new Date());

            tmpDoc = save(mDoc);
        }
        return tmpDoc;
    }

    private void verifyByTagName(String tag, VerifyBlock block) throws CouchbaseLiteException {
        Expression TAG_EXPR = Expression.property("tag");
        SelectResult DOCID = SelectResult.expression(Meta.id);
        DataSource ds = DataSource.database(db);
        Query q = QueryBuilder.select(DOCID).from(ds).where(TAG_EXPR.equalTo(Expression.string(tag)));
        ResultSet rs = q.execute();
        Result row;
        int n = 0;
        while ((row = rs.next()) != null) {
            block.verify(++n, row);
        }
    }

    private void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, new VerifyBlock() {
            @Override
            public void verify(int n, Result result) {
                count.incrementAndGet();
            }
        });
        assertEquals(nRows, count.intValue());
    }

    private int numIteration() {
        return (isEmulator() /*&& isARM()*/)
            ? 1000 // arm emulator
            : 2000; // real device
    }
}
