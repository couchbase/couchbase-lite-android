package com.couchbase.lite;

import com.couchbase.lite.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemoryTest extends LiteTestCaseWithDB {

    final static int DOC_SIZE = 10;
    final static int BATCH_DOC_SIZE = 100;
    final static int NUM_DOCS = 2000 * BATCH_DOC_SIZE;

    // NOTE: This unit test cause OOM error based on number of documents (NUM_DOCS)
    // https://github.com/couchbase/couchbase-lite-java-core/issues/1483
    public void testChangesRestWithManyDocs() throws CouchbaseLiteException, IOException {
        if (!memoryTestsEnabled())
            return;

        if (isUseForestDB()) return;

        Context ctx = getTestContext("memory");
        Manager mgr = new Manager(ctx, null);
        Database db;
        db = mgr.getExistingDatabase("huge");
        if (db == null || NUM_DOCS > db.getDocumentCount()) {
            DatabaseOptions ops = new DatabaseOptions();
            ops.setCreate(true);
            final Database tmp = mgr.openDatabase("huge", ops);
            char[] chars = new char[DOC_SIZE];
            Arrays.fill(chars, 'a');
            final String content = new String(chars);
            for (int j = 0; j < NUM_DOCS / BATCH_DOC_SIZE; j++) {
                boolean success = tmp.runInTransaction(new TransactionalTask() {
                    public boolean run() {
                        for (int i = 0; i < BATCH_DOC_SIZE; i++) {
                            try {
                                Map<String, Object> props = new HashMap<String, Object>();
                                props.put("content", content);
                                Document doc = tmp.createDocument();
                                doc.putProperties(props);
                            } catch (CouchbaseLiteException e) {
                                Log.e(TAG, "Error when creating a document", e);
                                return false;
                            }
                        }
                        return true;
                    }
                });
                assertTrue(success);
            }
            db = tmp;
        }
        assertEquals(NUM_DOCS, db.getDocumentCount());

        try {
            //Router router = new Router(mgr, (URLConnection) new URL("cblite://dummy").openConnection());
            //router.do_GET_Document_changes(db, null, null);

            this.manager = mgr;
            int changesSeq = 0;
            while (changesSeq < NUM_DOCS) {
                String url = String.format(Locale.ENGLISH, "/huge/_changes?since=%d", changesSeq);
                Map<String, Object> resp = (Map<String, Object>) send("GET", url, Status.OK, null);
                int lastSeq = (Integer) resp.get("last_seq");
                List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("results");
                assertEquals(changesSeq + 1, results.get(0).get("seq"));
                Log.e(TAG, "last_seq -> %d", lastSeq);
                changesSeq = lastSeq;
            }
            assertEquals(NUM_DOCS, changesSeq);

        } finally {
            if (db != null)
                db.close();
            if (mgr != null)
                mgr.close();
        }
    }
}
