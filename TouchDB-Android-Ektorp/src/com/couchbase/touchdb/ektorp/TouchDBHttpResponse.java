package com.couchbase.touchdb.ektorp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
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
    private OutputStream os;
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
        if(conn.isChunked()) {
            return is;
        } else {
            return new ByteArrayInputStream(((ByteArrayOutputStream)os).toByteArray());
        }
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
    public synchronized void onDataAvailable(byte[] data) {
        try {
            os.write(data);
            os.flush();
        } catch (IOException e) {
            Log.e(TDDatabase.TAG, "Error wrtiting data", e);
        }
    }

    @Override
    public void onFinish() {
        try {
            os.close();
        } catch (IOException e) {
            Log.e(TDDatabase.TAG, "Error wrtiting data", e);
        }

    }

    @Override
    public void onResponseReady() {
        doneSignal.countDown();
        if(conn.isChunked()) {
            is = new PipedInputStream();
            try {
                os = new PipedOutputStream((PipedInputStream)is);
            } catch (IOException e) {
                Log.e(TDDatabase.TAG, "Exception creating piped output stream", e);
            }
        } else {
            os = new ByteArrayOutputStream();
        }
    }

}
