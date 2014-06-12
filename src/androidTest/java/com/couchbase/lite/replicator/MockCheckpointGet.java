package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*

    Mock response to calling GET on a checkpoint where it returns a valid lastSequence
    (as opposed to returning a 404)

    {
       "_id":"_local/7d3186e30a82a3312fc2c54098a25ce568cd7dfb",
       "ok":true,
       "_rev":"0-1",
       "lastSequence":"2"
    }

 */
public class MockCheckpointGet {

    private String id;
    private String ok;
    private String rev;
    private String lastSequence;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOk() {
        return ok;
    }

    public void setOk(String ok) {
        this.ok = ok;
    }

    public String getRev() {
        return rev;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public String getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(String lastSequence) {
        this.lastSequence = lastSequence;
    }

    private Map<String, Object> generateMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("_id", getId());
        docMap.put("ok", getOk());
        docMap.put("_rev", getRev());
        docMap.put("lastSequence", getLastSequence());
        return docMap;
    }

    private String generateBody() {
        Map documentMap = generateMap();
        try {
            return Manager.getObjectMapper().writeValueAsString(documentMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MockResponse generateMockResponse() {
        MockResponse mockResponse = new MockResponse();
        mockResponse.setBody(generateBody());
        MockHelper.set200OKJson(mockResponse);
        return mockResponse;
    }


}
