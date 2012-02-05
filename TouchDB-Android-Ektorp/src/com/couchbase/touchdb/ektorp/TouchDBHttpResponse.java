package com.couchbase.touchdb.ektorp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.ektorp.http.HttpResponse;
import org.ektorp.util.Exceptions;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.router.TDRouter;
import com.couchbase.touchdb.router.TDURLConnection;

public class TouchDBHttpResponse implements HttpResponse {

    private TDURLConnection conn;
    private TDRouter router;

    public static TouchDBHttpResponse of(TDURLConnection conn, TDRouter router) {
        return new TouchDBHttpResponse(conn, router);
    }

    private TouchDBHttpResponse(TDURLConnection conn, TDRouter router) {
        this.conn = conn;
        this.router = router;
    }

    @Override
    public void abort() {
        router.stop();
    }

    @Override
    public int getCode() {
        return conn.getResponseCode();
    }

    @Override
    public InputStream getContent() {
        TDBody responseBody = conn.getResponseBody();
        if(responseBody != null) {
            byte[] responseBodyBytes = responseBody.getJson();
            return new ByteArrayInputStream(responseBodyBytes);
        }
        return null;
    }

    @Override
    public int getContentLength() {
        return conn.getContentLength();
    }

    @Override
    public String getContentType() {
        return conn.getContentType();
    }

    @Override
    public String getRequestURI() {
        try {
            return conn.getURL().toURI().toString();
        } catch (URISyntaxException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public boolean isSuccessful() {
        return conn.getResponseCode() < 300;
    }

    @Override
    public void releaseConnection() {

    }

}
