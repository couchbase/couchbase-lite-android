package com.couchbase.lite.replicator;


import com.couchbase.lite.Manager;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomizableMockHttpClient implements org.apache.http.client.HttpClient {

    // tests can register custom responders per url.  the key is the URL pattern to match,
    // the value is the responder that should handle that request.
    private Map<String, Responder> responders;

    // capture all request so that the test can verify expected requests were received.
    private List<HttpRequest> capturedRequests = Collections.synchronizedList(new ArrayList<HttpRequest>());

    // if this is set, it will delay responses by this number of milliseconds
    private long responseDelayMilliseconds;

    public CustomizableMockHttpClient() {
        responders = new HashMap<String, Responder>();
        addDefaultResponders();
    }

    public void setResponder(String urlPattern, Responder responder) {
        responders.put(urlPattern, responder);
    }

    public void setResponseDelayMilliseconds(long responseDelayMilliseconds) {
        this.responseDelayMilliseconds = responseDelayMilliseconds;
    }

    public void addDefaultResponders() {

        addResponderRevDiffsAllMissing();

        addResponderFakeBulkDocs();

        addResponderFakeLocalDocumentUpdate();

    }

    public void addResponderFailAllRequests(final int statusCode) {
        setResponder("*", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                return CustomizableMockHttpClient.emptyResponseWithStatusCode(statusCode);
            }
        });
    }

    public void addResponderThrowExceptionAllRequests() {
        setResponder("*", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                throw new IOException("Test IOException");
            }
        });
    }

    public void addResponderFakeLocalDocumentUpdate() {
        responders.put("_local", new Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                return CustomizableMockHttpClient.fakeLocalDocumentUpdate(httpUriRequest);
            }
        });
    }

    public void addResponderFakeBulkDocs() {
        responders.put("_bulk_docs", new Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                return CustomizableMockHttpClient.fakeBulkDocs(httpUriRequest);
            }
        });
    }

    public void addResponderRevDiffsAllMissing() {
        responders.put("_revs_diff", new Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                return CustomizableMockHttpClient.fakeRevsDiff(httpUriRequest);
            }
        });

    }

    public void addResponderReturnInvalidChangesFeedJson() {
        setResponder("_changes", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String json = "{\"results\":[";
                return CustomizableMockHttpClient.generateHttpResponseObject(json);
            }
        });
    }

    public void clearResponders() {
        responders = new HashMap<String, Responder>();
    }

    public void addResponderReturnEmptyChangesFeed() {
        setResponder("_changes", new CustomizableMockHttpClient.Responder() {
            @Override
            public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {
                String json = "{\"results\":[]}";
                return CustomizableMockHttpClient.generateHttpResponseObject(json);
            }
        });
    }


    public List<HttpRequest> getCapturedRequests() {
        return capturedRequests;
    }

    @Override
    public HttpParams getParams() {
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return null;
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {

        delayResponseIfNeeded();

        capturedRequests.add(httpUriRequest);

        for (String urlPattern : responders.keySet()) {
            if (urlPattern.equals("*") || httpUriRequest.getURI().getPath().contains(urlPattern)) {
                Responder responder = responders.get(urlPattern);
                return responder.execute(httpUriRequest);
            }
        }

        throw new RuntimeException("No responders matched for url pattern: " + httpUriRequest.getURI().getPath());

    }

    /*

    Request:

        {
            "lastSequence": "3"
        }

    Response:

        {
            "id": "_local/49b8682aa71e34628ebd9b3a8764fdbcfc9b03a6",
            "ok": true,
            "rev": "0-1"
        }

     */
    public static HttpResponse fakeLocalDocumentUpdate(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
        throw new IOException("Throw exception on purpose for purposes of testSaveRemoteCheckpointNoResponse()");
    }

    /*

    Request:

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

    Response:

    [{
        "id": "doc1-1384988871931",
        "rev": "2-e776a593-6b61-44ee-b51a-0bdf205c9e13"
    }, {
       ...
    }]

     */
    public static HttpResponse fakeBulkDocs(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {

        Map<String, Object> jsonMap = getJsonMapFromRequest((HttpPost) httpUriRequest);

        List<Map<String, Object>> responseList = new ArrayList<Map<String, Object>>();

        ArrayList<Map<String, Object>> docs = (ArrayList) jsonMap.get("docs");
        for (Map<String, Object> doc : docs) {
            Map<String, Object> responseListItem = new HashMap<String, Object>();
            responseListItem.put("id", doc.get("_id"));
            responseListItem.put("rev", doc.get("_rev"));
            responseList.add(responseListItem);
        }

        HttpResponse response = generateHttpResponseObject(responseList);
        return response;

    }

    /*

    Transform Request JSON:

        {
            "doc2-1384988871931": ["1-b52e6d59-4151-4802-92fb-7e34ceff1e92"],
            "doc1-1384988871931": ["2-e776a593-6b61-44ee-b51a-0bdf205c9e13"]
        }

    Into Response JSON:

        {
            "doc1-1384988871931": {
                "missing": ["2-e776a593-6b61-44ee-b51a-0bdf205c9e13"]
            },
            "doc2-1384988871931": {
                "missing": ["1-b52e6d59-4151-4802-92fb-7e34ceff1e92"]
            }
        }

     */
    public static HttpResponse fakeRevsDiff(HttpUriRequest httpUriRequest) throws IOException {

        Map<String, Object> jsonMap = getJsonMapFromRequest((HttpPost) httpUriRequest);

        Map<String, Object> responseMap = new HashMap<String, Object>();
        for (String key : jsonMap.keySet()) {
            ArrayList value = (ArrayList) jsonMap.get(key);
            Map<String, Object> missingMap = new HashMap<String, Object>();
            missingMap.put("missing", value);
            responseMap.put(key, missingMap);
        }

        HttpResponse response = generateHttpResponseObject(responseMap);
        return response;

    }

    public static HttpResponse generateHttpResponseObject(Object o) throws IOException {
        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        BasicStatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        HttpResponse response = responseFactory.newHttpResponse(statusLine, null);
        byte[] responseBytes = Manager.getObjectMapper().writeValueAsBytes(o);
        response.setEntity(new ByteArrayEntity(responseBytes));
        return response;
    }

    public static HttpResponse generateHttpResponseObject(String responseJson) throws IOException {
        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        BasicStatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        HttpResponse response = responseFactory.newHttpResponse(statusLine, null);
        byte[] responseBytes = responseJson.getBytes();
        response.setEntity(new ByteArrayEntity(responseBytes));
        return response;
    }

    public static HttpResponse emptyResponseWithStatusCode(int statusCode) {
        DefaultHttpResponseFactory responseFactory = new DefaultHttpResponseFactory();
        BasicStatusLine statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1, statusCode, "");
        HttpResponse response = responseFactory.newHttpResponse(statusLine, null);
        return response;
    }

    public static Map<String, Object> getJsonMapFromRequest(HttpPost httpUriRequest) throws IOException {
        HttpPost post = (HttpPost) httpUriRequest;
        InputStream is =  post.getEntity().getContent();
        return Manager.getObjectMapper().readValue(is, Map.class);
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

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException {
        throw new RuntimeException("Mock Http Client does not know how to handle this request.  It should be fixed");
    }

    static interface Responder {
        public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException;
    }

}
