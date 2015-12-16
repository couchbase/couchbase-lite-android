/**
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.mockserver;

import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

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

    private boolean isSticky;

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

            byte[] body = MockHelper.getUncompressedBody(request);
            Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(body);
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
        return this.isSticky;
    }

    @Override
    public long delayMs() {
        return 0;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }
}
