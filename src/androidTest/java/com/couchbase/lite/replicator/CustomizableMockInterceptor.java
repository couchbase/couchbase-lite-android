/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.replicator;

import com.couchbase.lite.Database;
import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by hideki on 5/16/16.
 */
public class CustomizableMockInterceptor implements Interceptor {

    interface Responder {
        Response execute(Request request) throws IOException;
    }

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private List<Request> capturedRequests = new CopyOnWriteArrayList<Request>();
    //private Map<String, ResponseData> responseData = new HashMap<String, ResponseData>();
    private Map<String, Responder> responders = new HashMap<String, Responder>();
    // if this is set, it will delay responses by this number of milliseconds
    private long responseDelayMilliseconds;

    public CustomizableMockInterceptor() {
        addDefaultResponders();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        capturedRequests.add(chain.request());
        return process(chain.request());
    }

    protected Response process(Request request) throws IOException {
        delayResponseIfNeeded();

        for (String key : responders.keySet()) {
            if (key.equals("*") || request.url().pathSegments().contains(key)) {
                return responders.get(key).execute(request);
            }
        }

        throw new RuntimeException("No responders matched for url pattern: "
                + request.url().pathSegments());
    }

    public void setResponseDelayMilliseconds(long responseDelayMilliseconds) {
        this.responseDelayMilliseconds = responseDelayMilliseconds;
    }

    private void delayResponseIfNeeded() {
        if (responseDelayMilliseconds > 0) {
            try {
                Thread.sleep(responseDelayMilliseconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Request> getCapturedRequests() {
        return capturedRequests;
    }

    public void clearCapturedRequests() {
        capturedRequests.clear();
    }

    public void clearResponders() {
        responders.clear();
    }

    public void setResponder(String urlPattern, Responder responder) {
        responders.put(urlPattern, responder);
    }


    public void addDefaultResponders() {
        addResponderRevDiffsAllMissing();
        addResponderFakeBulkDocs();
        //addResponderFakeLocalDocumentUpdateIOException();
    }

    public void addResponseReturnEmptyChangesFeed() {
        setResponder("_changes", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                String json = "{\"results\":[]}";
                return new Response.Builder()
                        .request(request)
                        .code(200)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(JSON, json)).build();
            }
        });
    }

    public void addResponderThrowExceptionAllRequests() {
        setResponder("*", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                throw new IOException("Test IOException");
            }
        });
    }

    public void addResponderReturnInvalidChangesFeedJson() {
        setResponder("_changes", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                String json = "{\"results\":[";
                return new Response.Builder()
                        .request(request)
                        .code(200)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(JSON, json)).build();
            }
        });
    }

    public void addResponderFakeLocalDocumentUpdate404() {
        setResponder("_local", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                String json = "{\"error\":\"not_found\",\"reason\":\"missing\"}";
                return new Response.Builder()
                        .request(request)
                        .code(404)
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(JSON, json)).build();
            }
        });
    }

    public void addResponderRevDiffsAllMissing() {
        setResponder("_revs_diff", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                return fakeRevsDiff(request);
            }
        });
    }

    public void addResponderFakeBulkDocs() {
        setResponder("_bulk_docs", new Responder() {
            @Override
            public Response execute(Request request) throws IOException {
                return fakeBulkDocs(request);
            }
        });
    }

    /*
    - /{db}/_bulk_docs
    Request JSON:
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

    Response JSON:
    [{
        "id": "doc1-1384988871931",
        "rev": "2-e776a593-6b61-44ee-b51a-0bdf205c9e13"
    }, {
       ...
    }]
    */
    public static Response fakeBulkDocs(Request request) throws IOException {
        Map<String, Object> jsonMap = OkHttpUtils.getJsonMapFromRequest(request);
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
        String json = Manager.getObjectMapper().writeValueAsString(responseList);
        return new Response.Builder()
                .request(request)
                .code(200)
                .protocol(Protocol.HTTP_1_1)
                .body(ResponseBody.create(JSON, json)).build();
    }

    /*
    - /{db}/_rev_diff
    Request JSON:
    {
        "doc2-1384988871931": ["1-b52e6d59-4151-4802-92fb-7e34ceff1e92"],
        "doc1-1384988871931": ["2-e776a593-6b61-44ee-b51a-0bdf205c9e13"]
    }
    Response JSON:
    {
        "doc1-1384988871931": {
            "missing": ["2-e776a593-6b61-44ee-b51a-0bdf205c9e13"]
        },
        "doc2-1384988871931": {
            "missing": ["1-b52e6d59-4151-4802-92fb-7e34ceff1e92"]
        }
    }
    */
    public static Response fakeRevsDiff(Request request) throws IOException {
        Map<String, Object> jsonMap = OkHttpUtils.getJsonMapFromRequest(request);
        Map<String, Object> responseMap = new HashMap<String, Object>();
        for (String key : jsonMap.keySet()) {
            ArrayList value = (ArrayList) jsonMap.get(key);
            Map<String, Object> missingMap = new HashMap<String, Object>();
            missingMap.put("missing", value);
            responseMap.put(key, missingMap);
        }
        String json = Manager.getObjectMapper().writeValueAsString(responseMap);
        return new Response.Builder()
                .request(request)
                .code(200)
                .protocol(Protocol.HTTP_1_1)
                .body(ResponseBody.create(JSON, json)).build();
    }
}
