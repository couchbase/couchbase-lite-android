package com.couchbase.lite.replicator;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.mockserver.MockBulkDocs;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockDocumentGet;
import com.couchbase.lite.mockserver.MockDocumentPut;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.mockserver.MockRevsDiff;
import com.couchbase.lite.support.MultipartReader;
import com.couchbase.lite.support.MultipartReaderDelegate;
import com.couchbase.lite.util.Utils;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.util.Arrays;
import java.util.Map;

/**
 * Created by hideki on 5/11/15.
 */
public class PushReplicationTest extends LiteTestCaseWithDB {

    /**
     * This test is too slow to run with ARM Emulator API 19 on Jenkins.
     * As default, test is disabled.
     * https://github.com/couchbase/couchbase-lite-java-core/issues/1108
     */
    public void manualTestPushWithManyAttachment() throws Exception {
        // add document
        String docId = "doc1";
        String docPathRegex = String.format("/db/%s.*", docId);
        String docAttachName = "attachment_%d.txt";
        String assetAttachName = "attach.txt";
        String contentType = "application/octet-stream";
        int numAttachments = 1025;
        Document doc = createDocumentForPushReplication(docId, assetAttachName, docAttachName, numAttachments, contentType);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
            server.play();

            // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(50);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // _revs_diff response -- everything missing
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // _bulk_docs response -- everything stored
            MockBulkDocs mockBulkDocs = new MockBulkDocs();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            // doc PUT responses for docs with attachments
            MockDocumentPut mockDocPut = new MockDocumentPut().setDocId(docId).setRev(doc.getCurrentRevisionId());
            dispatcher.enqueueResponse(docPathRegex, mockDocPut.generateMockResponse());

            // run replication
            Replication replication = database.createPushReplication(server.getUrl("/db"));
            replication.setContinuous(false);
            runReplication(replication);

            // make assertions about outgoing requests from replicator -> mock
            RecordedRequest getCheckpointRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
            assertTrue(getCheckpointRequest.getMethod().equals("GET"));
            assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));

            RecordedRequest revsDiffRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_REVS_DIFF);
            assertTrue(MockHelper.getUtf8Body(revsDiffRequest).contains(docId));

            RecordedRequest docPutRequest = dispatcher.takeRequest(docPathRegex);
            assertNotNull(docPutRequest);
            CustomMultipartReaderDelegate delegate = new CustomMultipartReaderDelegate();
            MultipartReader reader = new MultipartReader(docPutRequest.getHeader("Content-Type"), delegate);
            reader.appendData(docPutRequest.getBody());
            assertTrue(delegate.json.contains(docId));
        } finally {
            server.shutdown();
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-java-core/issues/614
     * <p/>
     * NOTE: To test json length is less than RemoteRequest.MIN_JSON_LENGTH_TO_COMPRESS
     * Need to modify MIN_JSON_LENGTH_TO_COMPRESS value to larger than 300.
     */
    public void testPushSmallDocWithAttachment() throws Exception {
        // add document
        String docId = "doc1";
        String docPathRegex = String.format("/db/%s.*", docId);
        String docAttachName = "attachment.png";
        String contentType = "image/png";
        Document doc = createDocumentForPushReplication(docId, docAttachName, contentType);

        // create mockwebserver and custom dispatcher
        MockDispatcher dispatcher = new MockDispatcher();
        MockWebServer server = MockHelper.getMockWebServer(dispatcher);
        dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
        try {
            server.play();

            // checkpoint GET response w/ 404 + respond to all PUT Checkpoint requests
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(50);
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // _revs_diff response -- everything missing
            MockRevsDiff mockRevsDiff = new MockRevsDiff();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_REVS_DIFF, mockRevsDiff);

            // _bulk_docs response -- everything stored
            MockBulkDocs mockBulkDocs = new MockBulkDocs();
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_BULK_DOCS, mockBulkDocs);

            // doc PUT responses for docs with attachments
            MockDocumentPut mockDocPut = new MockDocumentPut().setDocId(docId).setRev(doc.getCurrentRevisionId());
            dispatcher.enqueueResponse(docPathRegex, mockDocPut.generateMockResponse());

            // run replication
            Replication replication = database.createPushReplication(server.getUrl("/db"));
            replication.setContinuous(false);
            runReplication(replication);

            // make assertions about outgoing requests from replicator -> mock
            RecordedRequest getCheckpointRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_CHECKPOINT);
            assertTrue(getCheckpointRequest.getMethod().equals("GET"));
            assertTrue(getCheckpointRequest.getPath().matches(MockHelper.PATH_REGEX_CHECKPOINT));

            RecordedRequest revsDiffRequest = dispatcher.takeRequest(MockHelper.PATH_REGEX_REVS_DIFF);
            assertTrue(MockHelper.getUtf8Body(revsDiffRequest).contains(docId));

            RecordedRequest docPutRequest = dispatcher.takeRequest(docPathRegex);
            CustomMultipartReaderDelegate delegate = new CustomMultipartReaderDelegate();
            MultipartReader reader = new MultipartReader(docPutRequest.getHeader("Content-Type"), delegate);
            reader.appendData(docPutRequest.getBody());
            assertTrue(delegate.json.contains(docId));
            byte[] attachmentBytes = MockDocumentGet.getAssetByteArray(docAttachName);
            assertTrue(Arrays.equals(attachmentBytes, delegate.attachment));
        }finally{
            server.shutdown();
        }
    }

    protected Document createDocumentForPushReplication(String docId, String attachmentFileName, String attachmentContentType) throws CouchbaseLiteException {
        Document document = database.getDocument(docId);
        UnsavedRevision revision = document.createRevision();
        if (attachmentFileName != null) {
            revision.setAttachment(
                    attachmentFileName,
                    attachmentContentType,
                    getAsset(attachmentFileName)
            );
        }
        revision.save();
        return document;
    }

    protected Document createDocumentForPushReplication(String docId, String assetAttachmentFileName, String attachmentFileNameTemplate, int numAttachments, String attachmentContentType) throws CouchbaseLiteException {
        Document document = database.getDocument(docId);
        UnsavedRevision revision = document.createRevision();
        if (attachmentFileNameTemplate != null) {
            for(int i = 0; i < numAttachments; i++) {
                String attachmentFileName = String.format(attachmentFileNameTemplate, i);
                revision.setAttachment(
                        attachmentFileName,
                        attachmentContentType,
                        getAsset(assetAttachmentFileName)
                );
            }
        }
        revision.save();
        return document;
    }

    class CustomMultipartReaderDelegate implements MultipartReaderDelegate {
        public Map<String, String> headers = null;
        public byte[] attachment = null;
        public String json = null;
        public boolean gzipped = false;
        public boolean bJson = false;

        @Override
        public void startedPart(Map<String, String> headers) {
            gzipped = headers.get("Content-Encoding") != null && headers.get("Content-Encoding").contains("gzip");
            bJson = headers.get("Content-Type") != null && headers.get("Content-Type").contains("application/json");
        }

        @Override
        public void appendToPart(byte[] data) {
            try {
                if (gzipped && bJson) {
                    this.json = new String(Utils.decompressByGzip(data), "UTF-8");
                } else if (!gzipped && bJson) {
                    this.json = new String(data, "UTF-8");
                } else if (gzipped) {
                    this.attachment = Utils.decompressByGzip(data);
                } else {
                    this.attachment = data;
                }
            } catch (Exception ex) {
            }
        }

        @Override
        public void appendToPart(final byte[] data, int off, int len) {
            byte[] b = Arrays.copyOfRange(data, off, len - off);
            appendToPart(b);
        }

        @Override
        public void finishedPart() {
        }
    }
}
