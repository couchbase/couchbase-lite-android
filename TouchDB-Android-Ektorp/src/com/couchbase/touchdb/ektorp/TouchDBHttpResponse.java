package com.couchbase.touchdb.ektorp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;

import org.ektorp.http.HttpResponse;
import org.ektorp.util.Exceptions;

import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.router.TDRouter;
import com.couchbase.touchdb.router.TDRouterCallbackBlock;
import com.couchbase.touchdb.router.TDURLConnection;

public class TouchDBHttpResponse implements HttpResponse, TDRouterCallbackBlock {

    private TDURLConnection conn;
    private TDRouter router;
    private InputStream is;
    final CountDownLatch doneSignal = new CountDownLatch(1);

    public static TouchDBHttpResponse of(TDURLConnection conn, TDRouter router) throws IOException {
        return new TouchDBHttpResponse(conn, router);
    }

    private TouchDBHttpResponse(TDURLConnection conn, TDRouter router) throws IOException {
        this.conn = conn;
        this.router = router;
    }

    @Override
    public void abort() {
        router.stop();
    }

    @Override
    public int getCode() {
        getContent();
        return conn.getResponseCode();
    }

    @Override
    public InputStream getContent() {
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            Log.e(TDDatabase.TAG, "Interupted waiting for response", e);
        }
        return is;
    }

    @Override
    public int getContentLength() {
        getContent();
        return conn.getContentLength();
    }

    @Override
    public String getContentType() {
        getContent();
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
        try {
            router.stop();  // needed to clean up changes listeners
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /** TDRouterCallbackBlock **/

    @Override
    public void onResponseReady() {
        doneSignal.countDown();
        is = conn.getResponseInputStream();
    }

}
