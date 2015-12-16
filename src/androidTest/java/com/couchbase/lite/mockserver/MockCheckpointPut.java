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
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*

    Mock checkpoint response for a 201 PUT request to update the checkpoint

    {
       "id":"_local/7d3186e30a82a3312fc2c54098a25ce568cd7dfb",
       "ok":true,
       "rev":"0-1"
    }

 */
public class MockCheckpointPut implements SmartMockResponse {

    private String id;
    private String rev;
    private boolean isSticky;
    private long delayMs;

    private String getId() {
        return id;
    }

    private void setId(String id) {
        this.id = id;
    }

    private Map<String, Object> generateMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("id", getId());
        docMap.put("ok", true); // ditto
        docMap.put("rev", generateNextRev());
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

    /**
     * If the rev is empty, then generate rev "0-1"
     * If the rev is "0-1", then generate rev "0-2"
     * etc..
     *
     * @return the next rev to use, with respect to this.rev
     */
    private String generateNextRev() {
        if (getRev() == null) {
            return "0-0001";
        } else {
            StringTokenizer st = new StringTokenizer(getRev(), "-");
            String beforeDash = st.nextToken();
            String afterDash = st.nextToken();
            int afterDashInt = Integer.parseInt(afterDash);
            afterDashInt += 1;
            afterDash = String.format("%s", afterDashInt);
            return String.format("%s-%s", beforeDash, afterDash);
        }
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {

        MockResponse mockResponse = new MockResponse();

        if (request.getMethod().equals("PUT")) {

            // extract id from request
            // /db/_local/e11a8567a2ecaf27c52d02899fa82258a343d720 -> _local/e11a8567a2ecaf27c52d02899fa82258a343d720
            String path = request.getPath();
            String localDocId = "";
            Pattern pattern = Pattern.compile("/db/_local/(.*)");
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
                localDocId = matcher.group(1);
            } else {
                throw new RuntimeException(String.format("Could not extract local doc id from: %s", path));
            }

            // call setId
            setId(String.format("_local/%s", localDocId));

            // extract the _rev field from the request
            try {
                Map <String, Object> jsonMap = Manager.getObjectMapper().readValue(request.getUtf8Body(), Map.class);
                if (jsonMap.containsKey("_rev")) {
                    setRev((String)jsonMap.get("_rev"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            mockResponse.setBody(generateBody());
            MockHelper.set201OKJson(mockResponse);
            return mockResponse;

        } else if (request.getMethod().equals("GET")) {

            MockHelper.set404NotFoundJson(mockResponse);

        } else {
            throw new RuntimeException(String.format("Unexpected method: %s", request.getMethod()));
        }

        return mockResponse;

    }

    @Override
    public boolean isSticky() {
        return this.isSticky;
    }

    @Override
    public long delayMs() {
        return delayMs;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }

    public String getRev() {
        return rev;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
