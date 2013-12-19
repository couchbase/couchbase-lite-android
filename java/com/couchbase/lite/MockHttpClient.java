package com.couchbase.lite;


import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockHttpClient implements org.apache.http.client.HttpClient {

    private int numTimesExecuteCalled = 0;

    private List<HttpRequest> capturedRequests = Collections.synchronizedList(new ArrayList<HttpRequest>());

    public int getNumTimesExecuteCalled() {
        return numTimesExecuteCalled;
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
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        capturedRequests.add(httpUriRequest);
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        capturedRequests.add(httpUriRequest);
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        capturedRequests.add(httpRequest);
        throw new IOException("Test IOException");
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        numTimesExecuteCalled++;
        capturedRequests.add(httpRequest);
        throw new IOException("Test IOException");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        capturedRequests.add(httpUriRequest);
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        capturedRequests.add(httpUriRequest);
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        capturedRequests.add(httpRequest);
        throw new IOException("<T> Test IOException");
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        capturedRequests.add(httpRequest);
        throw new IOException("<T> Test IOException");
    }


}
