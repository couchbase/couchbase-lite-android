package com.couchbase.touchdb.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.codehaus.jackson.map.ObjectMapper;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;

public class TDRemoteRequest implements Runnable {
    private HandlerThread handlerThread;
    private Handler handler;
    private Thread thread;
    private final HttpClientFactory clientFactory;
    private String method;
    private URL url;
    private Object body;
    private TDRemoteRequestCompletionBlock onCompletion;

    public TDRemoteRequest(HttpClientFactory clientFactory, String method, URL url, Object body, TDRemoteRequestCompletionBlock onCompletion) {
        this.clientFactory = clientFactory;
        this.method = method;
        this.url = url;
        this.body = body;
        this.onCompletion = onCompletion;
    }

    public void start() {
        //first start a handler thread
        handlerThread = new HandlerThread("RemoteRequestHandlerThread");
        handlerThread.start();
        //Get the looper from the handlerThread
        Looper looper = handlerThread.getLooper();
        //Create a new handler - passing in the looper for it to use
        handler = new Handler(looper);
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        Log.v(TDDatabase.TAG, String.format("%s: %s .%s", toString(), method, url.toExternalForm()));
        HttpClient httpClient = clientFactory.getHttpClient();
        HttpUriRequest request = null;
        if(method.equalsIgnoreCase("GET")) {
            request = new HttpGet(url.toExternalForm());
        } else if(method.equalsIgnoreCase("PUT")) {
            request = new HttpPut(url.toExternalForm());
        } else if(method.equalsIgnoreCase("POST")) {
            request = new HttpPost(url.toExternalForm());
        }

        request.addHeader("Accept", "application/json");

        //set body if appropriate
        if(body != null && request instanceof HttpEntityEnclosingRequestBase) {
            ObjectMapper mapper = new ObjectMapper();
            byte[] bodyBytes = null;
            try {
                bodyBytes = mapper.writeValueAsBytes(body);
            } catch (Exception e) {
                Log.e(TDDatabase.TAG, "Error serializing body of request", e);
            }
            ByteArrayEntity entity = new ByteArrayEntity(bodyBytes);
            entity.setContentType("application/json");
            ((HttpEntityEnclosingRequestBase)request).setEntity(entity);
        }

        try {
            HttpResponse response = httpClient.execute(request);
            StatusLine status = response.getStatusLine();
            if(status.getStatusCode() >= 300) {
                Log.e(TDDatabase.TAG, "Got error " + Integer.toString(status.getStatusCode()));
                respondWithResult(null, new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()));
            } else {
                HttpEntity temp = response.getEntity();
                if(temp != null) {
                	try {
	                    InputStream stream = temp.getContent();
	                    ObjectMapper mapper = new ObjectMapper();
	                    Object fullBody = mapper.readValue(stream, Object.class);
	                    respondWithResult(fullBody, null);
                	} finally {
                		try { temp.consumeContent(); } catch (IOException e) {}
                	}
                }

            }

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            //Shut down the HandlerThread
            handlerThread.quit();
            handlerThread = null;
            handler = null;
        }

    }

    public void respondWithResult(final Object result, final Throwable error) {
        handler.post(new Runnable() {

            TDRemoteRequestCompletionBlock copy = onCompletion;

            @Override
            public void run() {
                onCompletion.onCompletion(result, error);
            }
        });
    }

}
