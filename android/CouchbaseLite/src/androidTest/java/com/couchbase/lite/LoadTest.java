
package com.couchbase.lite;

import org.junit.Test;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LoadTest extends BaseTest {
    private static final String TAG = LoadTest.class.getSimpleName();

    Document createDocumentWithTag(String id, String tag) {

        Document doc;
        if (id == null)
            doc = new Document();
        else
            doc = new Document(id);

        // Tag
        doc.setObject("tag", tag);

        // String
        doc.setObject("firstName", "Daniel");
        doc.setObject("lastName", "Tiger");

        // Dictionary:
        Dictionary address = new Dictionary();
        address.setObject("street", "1 Main street");
        address.setObject("city", "Mountain View");
        address.setObject("state", "CA");
        doc.setObject("address", address);

        // Array:
        Array phones = new Array();
        phones.addObject("650-123-0001");
        phones.addObject("650-123-0002");
        doc.setObject("phones", phones);

        // Date:
        doc.setObject("updated", new Date());

        return doc;
    }

    void createDocumentNSave(String id, String tag) throws CouchbaseLiteException {
        Document doc = createDocumentWithTag(id, tag);
        db.save(doc);
    }

    void createDocumentNSave(String tag, int nDocs) throws CouchbaseLiteException {
        for (int i = 0; i < nDocs; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%010d", i);
            createDocumentNSave(docID, tag);
        }
    }

    void updateDoc(Document doc, int rounds, String tag) throws CouchbaseLiteException {
        for (int i = 1; i <= rounds; i++) {
            doc.setObject("update", i);

            doc.setObject("tag", tag);

            Dictionary address = doc.getDictionary("address");
            assertNotNull(address);
            String street = String.format(Locale.ENGLISH, "%d street.", i);
            address.setObject("street", street);

            Array phones = doc.getArray("phones");
            assertNotNull(phones);
            assertEquals(2, phones.count());
            String phone = String.format(Locale.ENGLISH, "650-000-%04d", i);
            phones.setObject(0, phone);

            doc.setObject("updated", new Date());

            db.save(doc);
        }
    }

    interface VerifyBlock {
        void verify(int n, QueryResult result);
    }

    void verifyByTagName(String tag, VerifyBlock block) throws CouchbaseLiteException {
        Expression TAG_EXPR = Expression.property("tag");
        SelectResult DOCID = SelectResult.expression(Expression.meta().getId());
        DataSource ds = DataSource.database(db);
        Query q = Query.select(DOCID).from(ds).where(TAG_EXPR.equalTo(tag));
        Log.v(TAG, "query - > %s", q.explain());
        QueryResultSet rs = q.run();
        QueryResult row;
        int n = 0;
        while ((row = rs.next()) != null) {
            block.verify(++n, row);
        }
    }

    void verifyByTagName(String tag, int nRows) throws CouchbaseLiteException {
        final AtomicInteger count = new AtomicInteger(0);
        verifyByTagName(tag, new VerifyBlock() {
            @Override
            public void verify(int n, QueryResult result) {
                count.incrementAndGet();
            }
        });
        assertEquals(nRows, count.intValue());
    }

    @Test
    public void testCreate() throws InterruptedException, CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

        final int n = 2000;
        final String tag = "Create";
        createDocumentNSave(tag, n);
        verifyByTagName(tag, n);
        assertEquals(n, db.getCount());
    }

    @Test
    public void testUpdate() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

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
    }

    @Test
    public void testRead() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

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
    }

    @Test
    public void testDelete() throws CouchbaseLiteException {
        if (!config.loadTestsEnabled())
            return;

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
            doc.delete();
            assertEquals(0, db.getCount());
        }
    }
}
