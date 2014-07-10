package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class MockCheckpointGet implements SmartMockResponse {

    private String id;
    private String ok;
    private String rev;
    private String lastSequence;
    private boolean sticky;
    private boolean is404;

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

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {

        if (!request.getMethod().equals("GET")) {
            throw new RuntimeException("Expected GET, but was not a GET");
        }

        MockResponse mockResponse = new MockResponse();
        if (is404) {
            MockHelper.set404NotFoundJson(mockResponse);
            return mockResponse;
        }

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
        MockHelper.set200OKJson(mockResponse);
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return this.sticky;
    }

    @Override
    public long delayMs() {
        return 0;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public void set404(boolean is404) {
        this.is404 = is404;
    }
}
