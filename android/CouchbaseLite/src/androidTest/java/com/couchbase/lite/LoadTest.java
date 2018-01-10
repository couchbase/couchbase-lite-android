
package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LoadTest extends BaseTest {
    private static final String TAG = LoadTest.class.getSimpleName();

    protected void logPerformanceStats(String name, long time) {
        Log.e(TAG, "PerformanceStats: " + name + " -> " + time + " ms");
    }

    MutableDocument createDocumentWithTag(String id, String tag) {
        MutableDocument doc;
        if (id == null)
            doc = new MutableDocument();
        else
            doc = new MutableDocument(id);

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

    void createDocumentNSave(String id, String tag) throws CouchbaseLiteException {
        MutableDocument doc = createDocumentWithTag(id, tag);
        db.save(doc);
    }

    void createDocumentNSave(String tag, int nDocs) throws CouchbaseLiteException {
        for (int i = 0; i < nDocs; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%010d", i);
            createDocumentNSave(docID, tag);
        }
    }

    Document updateDoc(Document doc, int rounds, String tag) throws CouchbaseLiteException {
        Document tmpDoc = doc;
        for (int i = 1; i <= rounds; i++) {
            MutableDocument mDoc = tmpDoc.toMutable();
            mDoc.setValue("update", i);

            mDoc.setValue("tag", tag);

            MutableDictionary address = mDoc.getDictionary("address");
            assertNotNull(address);
            String street = String.format(Locale.ENGLISH, "%d street.", i);
            address.setValue("street", street);

            MutableArray phones = mDoc.getArray("phones");
            assertNotNull(phones);
            assertEquals(2, phones.count());
            String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
            phones.setValue(0, phone);

            mDoc.setValue("updated", new Date());

            tmpDoc = db.save(mDoc);
        }
        return tmpDoc;
    }

    interface VerifyBlock {
        void verify(int n, Result result);
    }

    void verifyByTagName(String tag, VerifyBlock block) throws CouchbaseLiteException {
        Expression TAG_EXPR = Expression.property("tag");
        SelectResult DOCID = SelectResult.expression(Meta.id);
        DataSource ds = DataSource.database(db);
        Query q = Query.select(DOCID).from(ds).where(TAG_EXPR.equalTo(tag));
        Log.v(TAG, "query - > %s", q.explain());
        ResultSet rs = q.execute();
        Result row;
        int n = 0;
        while ((row = rs.next()) != null) {
            block.verify(++n, row);
        }
    }

    void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, new VerifyBlock() {
            @Override
            public void verify(int n, Result result) {
                count.incrementAndGet();
            }
        });
        assertEquals(nRows, count.intValue());
    }

    @Test
    public void testCreate() throws InterruptedException, CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        final int n = 2000;
        final String tag = "Create";
        createDocumentNSave(tag, n);
        verifyByTagName(tag, n);
        assertEquals(n, db.getCount());

        logPerformanceStats("testCreate()", (System.currentTimeMillis() - start));
    }

    @Test
    public void testUpdate() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        final int n = 2000;
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

        logPerformanceStats("testUpdate()", (System.currentTimeMillis() - start));
    }

    @Test
    public void testRead() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        final int n = 2000;
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
    public void testDelete() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        final int n = 2000;
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
    public void testGlobalReferenceExcceded() throws InterruptedException, CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        // final int n = 20000; // num of docs;
        final int n = 2000; // NOTE: changed for unit test
        final int m = 100; // num of fields

        // Without Batch
        for (int i = 0; i < n; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.ENGLISH, "doc-%05d", i));
            for (int j = 0; j < m; j++) {
                doc.setInt(String.valueOf(j), j);
            }
            try {
                db.save(doc);
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Failed to save: %s", e, doc.getId());
            }
        }

        assertEquals(n, db.getCount());

        logPerformanceStats("testGlobalReferenceExcceded()", (System.currentTimeMillis() - start));
    }
}
