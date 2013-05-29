package com.couchbase.cblite.listener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.router.CBLRouter;
import com.couchbase.cblite.router.CBLRouterCallbackBlock;
import com.couchbase.cblite.router.CBLURLConnection;

@SuppressWarnings("serial")
public class CBLHTTPServlet extends HttpServlet {

    private CBLServer server;
    private CBLListener listener;
    public static final String TAG = "CBLHTTPServlet";

    public void setServer(CBLServer server) {
        this.server = server;
    }

    public void setListener(CBLListener listener) {
        this.listener = listener;
    }

    @Override
    public void service(HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {



        //set path
        String urlString = request.getRequestURI();
        String queryString = request.getQueryString();
        if(queryString != null) {
            urlString += "?" + queryString;
        }
        URL url = new URL("cblite://" +  urlString);
        final CBLURLConnection conn = (CBLURLConnection)url.openConnection();
        conn.setDoOutput(true);

        //set the method
        conn.setRequestMethod(request.getMethod());

        //set the headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            conn.setRequestProperty(headerName, request.getHeader(headerName));
        }

        //set the body
        InputStream is = request.getInputStream();
        //fixme dont think i should have to call available here
        //but its blocking on get requests otherwise
        if(is != null && is.available() > 0) {
            conn.setDoInput(true);
            conn.setRequestInputStream(is);
        }

        final ServletOutputStream os = response.getOutputStream();
        response.setBufferSize(128);
        Log.v(CBLDatabase.TAG, String.format("Buffer size is %d", response.getBufferSize()));

        final CountDownLatch doneSignal = new CountDownLatch(1);

        final CBLRouter router = new CBLRouter(server, conn);

        CBLRouterCallbackBlock callbackBlock = new CBLRouterCallbackBlock() {

            @Override
            public void onResponseReady() {
                //set the response code
                response.setStatus(conn.getResponseCode());

                //add the resonse headers
                Map<String, List<String>> headers = conn.getHeaderFields();
                if(headers != null) {
                    for (String headerName : headers.keySet()) {
                        for (String headerValue : headers.get(headerName)) {
                            response.addHeader(headerName, headerValue);
                        }
                    }
                }

                doneSignal.countDown();
            }

        };

        router.setCallbackBlock(callbackBlock);

        synchronized (server) {
            router.start();
        }

        try {
            doneSignal.await();
            InputStream responseInputStream = conn.getResponseInputStream();
            final byte[] buffer = new byte[65536];
            int r;
            while ((r = responseInputStream.read(buffer)) > 0) {
                os.write(buffer, 0, r);
                os.flush();
                response.flushBuffer();
            }
            os.close();
        } catch (InterruptedException e) {
            Log.e(CBLDatabase.TAG, "Interrupted waiting for result", e);
        } finally {
            if(router != null) {
                router.stop();
            }
        }

    }

}
