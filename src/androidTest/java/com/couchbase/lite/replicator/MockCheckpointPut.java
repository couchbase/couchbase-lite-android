package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
    private boolean isSticky;

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
        return 0;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }
}
