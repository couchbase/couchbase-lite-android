package com.couchbase.cblite.ektorp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.ektorp.http.HttpClient;
import org.ektorp.http.HttpResponse;
import org.ektorp.util.Exceptions;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.router.CBLRouter;
import com.couchbase.cblite.router.CBLURLConnection;

public class CBLiteHttpClient implements HttpClient {

    private CBLServer server;

    public CBLiteHttpClient(CBLServer server) {
        this.server = server;
    }

    protected URL urlFromUri(String uri) {
        URL url = null;
        try {
            url = new URL(String.format("cblite://%s", uri));
        } catch (MalformedURLException e) {
            Log.w(CBLDatabase.TAG, "Unable to build CBLite URL", e);
            throw Exceptions.propagate(e);
        }
        return url;
    }

    protected CBLURLConnection connectionFromUri(String uri) {
        CBLURLConnection conn = null;
        URL url = urlFromUri(uri);
        try {
            conn = (CBLURLConnection) url.openConnection();
            conn.setDoOutput(true);
        } catch (IOException e) {
            Exceptions.propagate(e);
        }
        return conn;
    }

    @Override
    public HttpResponse delete(String uri) {
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("DELETE");
            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse get(String uri) {
        CBLURLConnection conn = connectionFromUri(uri);
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
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("HEAD");
            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse post(String uri, String content) {
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("POST");

            if (content != null) {
                conn.setDoInput(true);
                conn.setRequestInputStream(new ByteArrayInputStream(content
                        .getBytes()));
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse post(String uri, InputStream contentStream) {
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("POST");

            if (contentStream != null) {
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
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse put(String uri, String content) {
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            if (content != null) {
                conn.setDoInput(true);
                conn.setRequestInputStream(new ByteArrayInputStream(content
                        .getBytes()));
            }

            return executeRequest(conn);
        } catch (ProtocolException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public HttpResponse put(String uri, InputStream contentStream,
            String contentType, long contentLength) {
        CBLURLConnection conn = connectionFromUri(uri);
        try {
            conn.setRequestMethod("PUT");

            if (contentStream != null) {
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

    protected HttpResponse executeRequest(CBLURLConnection conn) {
        final CBLRouter router = new CBLRouter(server, conn);
        CBLiteHttpResponse response;
        try {
            response = CBLiteHttpResponse.of(conn, router);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        router.setCallbackBlock(response);
        ScheduledExecutorService workExecutor = server.getWorkExecutor();
        Future<?> routerFuture = workExecutor.submit(new Runnable() {
            @Override
            public void run() {
                router.start();
            }
        });
        try {
            routerFuture.get();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (ExecutionException e) {
            throw Exceptions.propagate(e);
        }
        return response;
    }

}
