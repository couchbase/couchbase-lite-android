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

    private boolean isSticky;

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
            byte[] body = MockHelper.getUncompressedBody(request);
            Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(body);

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
