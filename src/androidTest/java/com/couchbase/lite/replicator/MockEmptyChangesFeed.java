package com.couchbase.lite.replicator;

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by eduardo.santos on 09-07-2014.
 */

/*
 Changes feed empty response with last_seq = 0
 {
    "results":[],
    "last_seq":0
 }
 */
public class MockEmptyChangesFeed implements SmartMockResponse {

    private boolean permanentResponse = false;

    private Map<String, Object> generateMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("results", new ArrayList<String>());
        docMap.put("last_seq", 0);
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

        MockResponse mockResponse = new MockResponse();;
        mockResponse.setBody(generateBody());
        MockHelper.set200OKJson(mockResponse);
        return mockResponse;

    }

    @Override
    public boolean isSticky() {
        return isPermanentResponse();
    }

    public boolean isPermanentResponse() {
        return permanentResponse;
    }

    public void setPermanentResponse(boolean permanentResponse) {
        this.permanentResponse = permanentResponse;
    }

}
