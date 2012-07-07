package com.couchbase.touchdb.ektorp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import org.ektorp.http.HttpClient;
import org.ektorp.http.HttpResponse;
import org.ektorp.util.Exceptions;

import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.router.TDRouter;
import com.couchbase.touchdb.router.TDURLConnection;

public class TouchDBHttpClient implements HttpClient {

    private TDServer server;

    public TouchDBHttpClient(TDServer server) {
        this.server = server;
    }

    protected URL urlFromUri(String uri) {
        URL url = null;
        try {
            url = new URL(String.format("touchdb://%s", uri));
        } catch (MalformedURLException e) {
            Log.w(TDDatabase.TAG, "Unable to build TouchDB URL", e);
            throw Exceptions.propagate(e);
        }
        return url;
    }

    protected TDURLConnection connectionFromUri(String uri) {
        TDURLConnection conn = null;
        URL url = urlFromUri(uri);
        try {
            conn = (TDURLConnection)url.openConnection();
            conn.setDoOutput(true);
        } catch (IOException e) {
            Exceptions.propagate(e);
        }
        return conn;
    }

    @Override
    public HttpResponse delete(String uri) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("DELETE");
            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse get(String uri) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("GET");
            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse getUncached(String uri) {
        return get(uri);
    }

    @Override
    public HttpResponse head(String uri) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("HEAD");
            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse post(String uri, String content) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("POST");

            if(content != null) {
                conn.setDoInput(true);
                conn.setRequestInputStream(new ByteArrayInputStream(content.getBytes()));
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse post(String uri, InputStream contentStream) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("POST");

            if(contentStream != null) {
                conn.setDoInput(true);
                conn.setRequestInputStream(contentStream);
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse postUncached(String uri, String content) {
        return post(uri, content);
    }

    @Override
    public HttpResponse put(String uri) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse put(String uri, String content) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            if(content != null) {
                conn.setDoInput(true);
                conn.setRequestInputStream(new ByteArrayInputStream(content.getBytes()));
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse put(String uri, InputStream contentStream, String contentType, long contentLength) {
        TDURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            if(contentStream != null) {
                conn.setDoInput(true);
                conn.setRequestProperty("content-type", contentType);
                conn.setRequestInputStream(contentStream);
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void shutdown() {

    }

    protected HttpResponse executeRequest(TDURLConnection conn) {
        TDRouter router = new TDRouter(server, conn);
        TouchDBHttpResponse response;
        try {
            response = TouchDBHttpResponse.of(conn, router);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        router.setCallbackBlock(response);
        synchronized(server) {
            router.start();
        }
        return response;
    }

}
