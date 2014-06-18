package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*

    Generate mock document PUT response, eg

    {
       "id":"doc2-1403051744202",
       "ok":true,
       "rev":"2-6aa304118ef11cf427ec90ea9ba7ec09"
    }

    This could be completely "dynamic" and generated from the request.
    It would need to support parsing multipart requests (or use a regex).
    So for now, it just takes the id and rev as params.

 */
public class MockDocumentPut {

    private String docId;
    private String rev;

    public String getDocId() {
        return docId;
    }

    public MockDocumentPut setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public String getRev() {
        return rev;
    }

    public MockDocumentPut setRev(String rev) {
        this.rev = rev;
        return this;
    }

    private Map<String, Object> generateDocumentMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("id", getDocId());
        docMap.put("ok", true);
        docMap.put("rev", getRev());
        return docMap;
    }

    private String generateDocumentBody() {
        Map documentMap = generateDocumentMap();
        try {
            return Manager.getObjectMapper().writeValueAsString(documentMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MockResponse generateMockResponse() {

        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(generateDocumentBody());
        MockHelper.set200OKJson(mockResponse);

        return mockResponse;
    }


}
