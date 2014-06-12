package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*

    Mock checkpoint response for a 201 PUT request to update the checkpoint

    {
       "id":"_local/7d3186e30a82a3312fc2c54098a25ce568cd7dfb",
       "ok":true,
       "rev":"0-1"
    }

 */
public class MockCheckpointPut {

    private Map<String, Object> generateMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("id", "_local/7d3186e30a82a3312fc2c54098a25ce568cd7dfb");  // ignored by replicator AFAIK, use fake val
        docMap.put("ok", true); // ditto
        docMap.put("rev", "0-1"); // ditto
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
        MockHelper.set201OKJson(mockResponse);
        return mockResponse;
    }

}
