package com.couchbase.lite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseTest extends LiteTestCase {

    public void testPruneRevsToMaxDepth() throws Exception {

        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("testName", "testDatabaseCompaction");
        properties.put("tag", 1337);

        Document doc=createDocumentWithProperties(database, properties);
        SavedRevision rev = doc.getCurrentRevision();

        database.setMaxRevTreeDepth(1);
        for (int i=0; i<10; i++) {
            Map<String,Object> properties2 = new HashMap<String,Object>(properties);
            properties2.put("tag", i);
            rev = rev.createRevision(properties2);
        }

        int numPruned = database.pruneRevsToMaxDepth(1);
        assertEquals(9, numPruned);

        Document fetchedDoc = database.getDocument(doc.getId());
        List<SavedRevision> revisions = fetchedDoc.getRevisionHistory();
        assertEquals(1, revisions.size());

        numPruned = database.pruneRevsToMaxDepth(1);
        assertEquals(0, numPruned);

    }

}
