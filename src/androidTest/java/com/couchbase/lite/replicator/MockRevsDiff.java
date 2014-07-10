package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * Eg, given
 */

/*
    By default, return a _revs_diff response which says everything is missing.

    Given:

    {
       "doc1-1403051744202":[
          "2-3ec9ce8f9d323071505e895ba56ec946"
       ],
       "doc2-1403051744202":[
          "2-6aa304118ef11cf427ec90ea9ba7ec09"
       ]
    }

    Return:

    {
       "doc1-1403051744202":{
          "missing":[
             "2-3ec9ce8f9d323071505e895ba56ec946"
          ]
       },
       "doc2-1403051744202":{
          "missing":[
             "2-6aa304118ef11cf427ec90ea9ba7ec09"
          ]
       }
    }

 */
public class MockRevsDiff implements SmartMockResponse {

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {

        if (!request.getMethod().equals("POST")) {
            throw new RuntimeException(String.format("Expected POST, got %s", request.getMethod()));
        }

        if (!request.getPath().contains("_revs_diff")) {
            throw new RuntimeException(String.format("Expected _revs_diff in path, got %s", request.getPath()));
        }

        try {

            MockResponse mockResponse = new MockResponse();

            Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(request.getBody());
            Log.d(Log.TAG, "MockRevsDiff jsonMap: %s", jsonMap);

            Map<String, Object> responseMap = new HashMap<String, Object>();
            for (String key : jsonMap.keySet()) {
                ArrayList value = (ArrayList) jsonMap.get(key);
                Map<String, Object> missingMap = new HashMap<String, Object>();
                missingMap.put("missing", value);
                responseMap.put(key, missingMap);
            }

            mockResponse.setBody(Manager.getObjectMapper().writeValueAsBytes(responseMap));
            MockHelper.set200OKJson(mockResponse);
            return mockResponse;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    @Override
    public boolean isSticky() {
        return false;
    }

    @Override
    public long delayMs() {
        return 0;
    }
}
