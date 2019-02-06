package com.couchbase.lite;

import com.couchbase.litecore.C4Error;
import com.couchbase.litecore.C4ReplicatorStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReplicatorMisc extends BaseReplicatorTest {

    @Test
    public void testReplicatorChange() {
        String docID = "someDocumentID";
        long completed = 10;
        long total = 20;
        int errorCode = CBLError.Code.CBLErrorBusy;
        int errorDomain = 1; // CBLError.Domain.CBLErrorDomain: LiteCoreDomain
        C4ReplicatorStatus c4ReplicatorStatus = new C4ReplicatorStatus(
                AbstractReplicator.ActivityLevel.CONNECTING.getValue(),
                completed,
                total,
                1,
                errorDomain,
                errorCode,
                0
        );
        Replicator.Status status = new Replicator.Status(c4ReplicatorStatus);
        ReplicatorChange repChange = new ReplicatorChange(repl, status);

        assertEquals(repChange.getReplicator(), repl);
        assertEquals(repChange.getStatus(), status);
        assertEquals(repChange.getStatus().getActivityLevel(), status.getActivityLevel());
        assertEquals(repChange.getStatus().getProgress().getCompleted(), completed);
        assertEquals(repChange.getStatus().getProgress().getTotal(), total);
        assertEquals(repChange.getStatus().getError().getCode(), errorCode);
        assertEquals(repChange.getStatus().getError().getDomain(), CBLError.Domain.CBLErrorDomain);
        String changeInString = "ReplicatorChange{" +
                "replicator=" + repl +
                ", status=" + status +  '}';
        assertEquals(repChange.toString(), changeInString);
    }

    @Test
    public void testDocumentReplication() {
        boolean isPush = true;
        List<ReplicatedDocument> docs = new ArrayList<>();
        DocumentReplication doc = new DocumentReplication(repl, isPush, docs);
        assertEquals(doc.isPush(), isPush);
        assertEquals(doc.getReplicator(), repl);
        assertEquals(doc.getDocuments(), docs);
    }

    @Test
    public void testReplicatedDocument() {
        String docID = "someDocumentID";
        int flags = C4DocumentFlags.kDocDeleted;
        C4Error error = new C4Error(1, CBLError.Code.CBLErrorBusy, 0);
        ReplicatedDocument doc = new ReplicatedDocument(docID, flags, error, true);

        assertEquals(doc.getID(), docID);
        assertTrue(doc.flags().contains(DocumentFlag.DocumentFlagsDeleted));
        assertEquals(doc.getError().getDomain(), CBLError.Domain.CBLErrorDomain);
        assertEquals(doc.getError().getCode(),  CBLError.Code.CBLErrorBusy);

        String replicatedDocumentInString = "ReplicatedDocument {" +
                ", document id =" + docID +
                ", error code =" + error.getCode()+
                ", error domain=" + error.getDomain() +
                '}';
        assertEquals(doc.toString(), replicatedDocumentInString);
    }
}
