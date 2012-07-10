package com.couchbase.touchdb.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import android.os.Handler;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;

public class TDRemoteRequest implements Runnable {

    private Handler handler;
    private Thread thread;
    private final HttpClientFactory clientFactory;
    private String method;
    private URL url;
    private Object body;
    private TDRemoteRequestCompletionBlock onCompletion;

    public TDRemoteRequest(Handler handler, HttpClientFactory clientFactory, String method, URL url, Object body, TDRemoteRequestCompletionBlock onCompletion) {
        this.clientFactory = clientFactory;
        this.method = method;
        this.url = url;
        this.body = body;
        this.onCompletion = onCompletion;
        this.handler = handler;
    }

    public void start() {
        thread = new Thread(this, "RemoteRequest-" + url.toExternalForm());
        thread.start();
    }

    @Override
    public void run() {
        HttpClient httpClient = clientFactory.getHttpClient();
        ClientConnectionManager manager = httpClient.getConnectionManager();

        HttpUriRequest request = null;
        if(method.equalsIgnoreCase("GET")) {
            request = new HttpGet(url.toExternalForm());
        } else if(method.equalsIgnoreCase("PUT")) {
            request = new HttpPut(url.toExternalForm());
        } else if(method.equalsIgnoreCase("POST")) {
            request = new HttpPost(url.toExternalForm());
        }

        // if the URL contains user info AND if this a DefaultHttpClient
        // then preemptively set the auth credentials
        if(url.getUserInfo() != null) {
            if(url.getUserInfo().contains(":")) {
                String[] userInfoSplit = url.getUserInfo().split(":");
                final Credentials creds = new UsernamePasswordCredentials(userInfoSplit[0], userInfoSplit[1]);
                if(httpClient instanceof DefaultHttpClient) {
                    DefaultHttpClient dhc = (DefaultHttpClient)httpClient;

                    HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {

                        @Override
                        public void process(HttpRequest request,
                                HttpContext context) throws HttpException,
                                IOException {
                            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);
                            CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(
                                    ClientContext.CREDS_PROVIDER);
                            HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);

                            if (authState.getAuthScheme() == null) {
                                AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
                                authState.setAuthScheme(new BasicScheme());
                                authState.setCredentials(creds);
                            }
                        }
                    };

                    dhc.addRequestInterceptor(preemptiveAuth, 0);
                }
            }
            else {
                Log.w(TDDatabase.TAG, "Unable to parse user info, not setting credentials");
            }
        }

        request.addHeader("Accept", "application/json");

        //set body if appropriate
        if(body != null && request instanceof HttpEntityEnclosingRequestBase) {
            byte[] bodyBytes = null;
            try {
                bodyBytes = TDServer.getObjectMapper().writeValueAsBytes(body);
            } catch (Exception e) {
                Log.e(TDDatabase.TAG, "Error serializing body of request", e);
            }
            ByteArrayEntity entity = new ByteArrayEntity(bodyBytes);
            entity.setContentType("application/json");
            ((HttpEntityEnclosingRequestBase)request).setEntity(entity);
        }

        Object fullBody = null;
        Throwable error = null;
        try {
            HttpResponse response = httpClient.execute(request);
            StatusLine status = response.getStatusLine();
            if(status.getStatusCode() >= 300) {
                Log.e(TDDatabase.TAG, "Got error " + Integer.toString(status.getStatusCode()));
                Log.e(TDDatabase.TAG, "Request was for: " + request.toString());
                Log.e(TDDatabase.TAG, "Status reason: " + status.getReasonPhrase());
                error = new HttpResponseException(status.getStatusCode(), status.getReasonPhrase());
            } else {
                HttpEntity temp = response.getEntity();
                if(temp != null) {
                	try {
	                    InputStream stream = temp.getContent();
	                    fullBody = TDServer.getObjectMapper().readValue(stream, Object.class);
                	} finally {
                		try { temp.consumeContent(); } catch (IOException e) {}
                	}
                }
            }
        } catch (ClientProtocolException e) {
            error = e;
        } catch (IOException e) {
            error = e;
        }
        respondWithResult(fullBody, error);
    }

    public void respondWithResult(final Object result, final Throwable error) {
        if(handler != null) {
            handler.post(new Runnable() {

                TDRemoteRequestCompletionBlock copy = onCompletion;

                @Override
                public void run() {
                    try {
                        onCompletion.onCompletion(result, error);
                    } catch(Exception e) {
                        // don't let this crash the thread
                        Log.e(TDDatabase.TAG, "TDRemoteRequestCompletionBlock throw Exception", e);
                    }
                }
            });
        }
    }

}
