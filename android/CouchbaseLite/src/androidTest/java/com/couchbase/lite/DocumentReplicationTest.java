package com.couchbase.lite;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DocumentReplicationTest extends BaseReplicatorTest {

    @Test
    public void testDocumentReplication() {
        boolean isPush = true;
        List<ReplicatedDocument> docs = new ArrayList<>();
        DocumentReplication doc = new DocumentReplication(repl, isPush, docs);
        assertEquals(doc.isPush(), isPush);
        assertEquals(doc.getReplicator(), repl);
        assertEquals(doc.getDocuments(), docs);
    }
}
