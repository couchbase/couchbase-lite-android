package com.couchbase.lite.replicator;

import com.couchbase.lite.Database;
import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*

    Fake bulk docs response that indicates everything was stored

    Given:

    {
        "new_edits": false,
        "docs": [{
            "_rev": "2-e776a593-6b61-44ee-b51a-0bdf205c9e13",
            "foo": 1,
            "_id": "doc1-1384988871931",
            "_revisions": {
                "start": 2,
                "ids": ["e776a593-6b61-44ee-b51a-0bdf205c9e13", "38af0cab-d397-4b68-a59e-67c341f98dc4"]
            }
        }, {

        ...

        }
    }

    Return:

    [{
        "id": "doc1-1384988871931",
        "rev": "2-e776a593-6b61-44ee-b51a-0bdf205c9e13"
    }, {
       ...
    }]

 */
public class MockBulkDocs implements SmartMockResponse {

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {

        if (!request.getMethod().equals("POST")) {
            throw new RuntimeException(String.format("Expected POST, got %s", request.getMethod()));
        }

        if (!request.getPath().contains("_bulk_docs")) {
            throw new RuntimeException(String.format("Expected _bulk_docs in path, got %s", request.getPath()));
        }

        try {

            MockResponse mockResponse = new MockResponse();

            Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(request.getBody());

            List<Map<String, Object>> responseList = new ArrayList<Map<String, Object>>();

            ArrayList<Map<String, Object>> docs = (ArrayList) jsonMap.get("docs");
            for (Map<String, Object> doc : docs) {
                Map<String, Object> responseListItem = new HashMap<String, Object>();
                responseListItem.put("id", doc.get("_id"));
                responseListItem.put("rev", doc.get("_rev"));
                Log.d(Database.TAG, "id: " + doc.get("_id"));
                Log.d(Database.TAG, "rev: " + doc.get("_rev"));
                responseList.add(responseListItem);
            }

            mockResponse.setBody(Manager.getObjectMapper().writeValueAsBytes(responseList));
            MockHelper.set200OKJson(mockResponse);
            return mockResponse;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static Map<String, Object> findDocById(Map<String, Object> bulkDocsJson, String doc4Id) {
        List<Map> docs = (List) bulkDocsJson.get("docs");
        for (Map doc : docs) {
            String id = (String) doc.get("_id");
            if (id.equals(doc4Id)) {
                return doc;
            }
        }

        throw new RuntimeException(String.format("Can't find doc w/ id: %s in %s", doc4Id, bulkDocsJson));
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
