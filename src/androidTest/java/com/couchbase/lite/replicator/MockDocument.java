package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/*

    Generate mock document, eg

    {
       "_id":"doc1-1402588904847",
       "_rev":"1-d57b1bc60eb9273c3349d932e15f9949",
       "_revisions":{
          "ids":[
             "d57b1bc60eb9273c3349d932e15f9949"
          ],
          "start":1
       },
       "bar":false,
       "foo":1
    }

    Limitations: it cannot represent docs with revision histories longer
    than one rev.

 */
public class MockDocument {

    private String docId;
    private String rev;
    private Map<String, Object> jsonMap;

    public String getDocId() {
        return docId;
    }

    public MockDocument setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public String getRev() {
        return rev;
    }

    public MockDocument setRev(String rev) {
        this.rev = rev;
        return this;
    }

    public Map<String, Object> getJsonMap() {
        return jsonMap;
    }

    public MockDocument setJsonMap(Map<String, Object> jsonMap) {
        this.jsonMap = jsonMap;
        return this;
    }

    private Map<String, Object> generateRevHistoryMap() {
        Map<String, Object> revHistoryMap = new HashMap<String, Object>();
        // parse rev into components, eg
        String[] revComponents = rev.split("-");
        String numericPrefixStr = revComponents[0];  // eg, "1"
        String digest = revComponents[1]; // eg, "d57b1bc60eb9273c3349d932e15f9949"
        int numericPrefix = Integer.parseInt(numericPrefixStr);
        List<String> revHistoryDigestIds = new ArrayList<String>();
        revHistoryDigestIds.add(digest);
        revHistoryMap.put("ids", revHistoryDigestIds);
        revHistoryMap.put("start", numericPrefix);
        return revHistoryMap;
    }

    private Map<String, Object> generateDocumentMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("_id", getDocId());
        docMap.put("_rev", getRev());
        docMap.put("_revisions", generateRevHistoryMap());
        docMap.putAll(jsonMap);
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
