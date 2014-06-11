package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

}
