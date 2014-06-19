package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*

    Generate mock changes feed, eg:

    {
       "results":[
          {
             "seq":1,
             "id":"doc1-1402588904847",
             "changes":[
                {
                   "rev":"1-d57b1bc60eb9273c3349d932e15f9949"
                }
             ]
          },
          {
             "seq":2,
             "id":"doc2-1402588904847",
             "changes":[
                {
                   "rev":"1-d57b1bc60eb9273c3349d932e15f9949"
                }
             ]
          }
       ],
       "last_seq":2
    }

 */
public class MockChangesFeed {

    private List<MockChangedDoc> mockChangedDocs;

    public MockChangesFeed() {
        mockChangedDocs = new ArrayList<MockChangedDoc>();
    }

    public void add(MockChangedDoc mockChangedDoc) {
        mockChangedDocs.add(mockChangedDoc);
    }

    private int getHighestSeq() {
        if (mockChangedDocs.size() == 0) {
            return -1;
        }
        // just assume they were added in the right order,
        // so get the last one and use that
        int indexLast = mockChangedDocs.size() - 1;
        MockChangedDoc lastDoc = mockChangedDocs.get(indexLast);
        return lastDoc.getSeq();
    }

    private Map<String, Object> generateChangesMap() {
        Map<String, Object> changesMap = new HashMap<String, Object>();
        int highestSeq = getHighestSeq();
        if (highestSeq > 0) {
            changesMap.put("last_seq", highestSeq);
        }
        List results = new ArrayList();
        for (MockChangedDoc mockChangedDoc : mockChangedDocs) {
            results.add(mockChangedDoc.exportAsMap());
        }
        changesMap.put("results", results);
        return changesMap;
    }

    private String generateChangesBody() {
        Map changesMap = generateChangesMap();
        try {
            return Manager.getObjectMapper().writeValueAsString(changesMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MockResponse generateMockResponse() {
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(generateChangesBody());
        MockHelper.set200OKJson(mockResponse);
        return mockResponse;
    }

    public static class MockChangedDoc {

        private int seq;
        private String docId;
        private List<String> changedRevIds;

        public MockChangedDoc() {
        }

        public MockChangedDoc(MockDocumentGet.MockDocument mockDocument) {
            this();
            this.seq = mockDocument.getDocSeq();
            this.docId = mockDocument.getDocId();
            this.changedRevIds = Arrays.asList(mockDocument.getDocRev());
        }

        public MockChangedDoc setSeq(int seq) {
            this.seq = seq;
            return this;
        }

        public MockChangedDoc setDocId(String docId) {
            this.docId = docId;
            return this;
        }

        public MockChangedDoc setChangedRevIds(List<String> changedRevIds) {
            this.changedRevIds = changedRevIds;
            return this;
        }

        public int getSeq() {
            return seq;
        }

        public String getDocId() {
            return docId;
        }

        public List<String> getChangedRevIds() {
            return changedRevIds;
        }

        /**
         * Export as a map
         *
         * @return map, eg {"seq":2,"id":"doc2","changes":[{"rev":"1-5e38"}]}
         */
        public Map<String, Object> exportAsMap() {
            Map<String, Object> exported = new HashMap<String, Object>();
            exported.put("seq", getSeq());
            exported.put("id", getDocId());
            List changes = new ArrayList();
            for (String changeRevId : changedRevIds) {
                Map<String, Object> revIdMap = new HashMap<String, Object>();
                revIdMap.put("rev", changeRevId);
                changes.add(revIdMap);
            }
            exported.put("changes", changes);
            return exported;
        }

    }


}


