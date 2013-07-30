package com.couchbase.cblite.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.Header;
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
import org.apache.http.client.CookieStore;
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
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import android.os.Handler;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;

public class CBLRemoteRequest implements Runnable {

    protected ScheduledExecutorService workExecutor;
    protected final HttpClientFactory clientFactory;
    protected String method;
    protected URL url;
    protected Object body;
    protected CBLRemoteRequestCompletionBlock onCompletion;
    protected BasicHttpContext httpContext;

    public CBLRemoteRequest(ScheduledExecutorService workExecutor,
                            HttpClientFactory clientFactory, String method, URL url,
                            Object body, CBLRemoteRequestCompletionBlock onCompletion,
                            BasicHttpContext httpContext) {
        this.clientFactory = clientFactory;
        this.method = method;
        this.url = url;
        this.body = body;
        this.onCompletion = onCompletion;
        this.workExecutor = workExecutor;
        this.httpContext = httpContext;
    }

    @Override
    public void run() {

        HttpClient httpClient = clientFactory.getHttpClient();

        ClientConnectionManager manager = httpClient.getConnectionManager();

        HttpUriRequest request = createConcreteRequest();

        preemptivelySetAuthCredentials(httpClient);

        request.addHeader("Accept", "application/json");

        setBody(request);

        executeRequest(httpClient, request);

    }

    private HttpUriRequest createConcreteRequest() {
        HttpUriRequest request = null;
        if (method.equalsIgnoreCase("GET")) {
            request = new HttpGet(url.toExternalForm());
        } else if (method.equalsIgnoreCase("PUT")) {
            request = new HttpPut(url.toExternalForm());
        } else if (method.equalsIgnoreCase("POST")) {
            request = new HttpPost(url.toExternalForm());
        }
        return request;
    }

    private void setBody(HttpUriRequest request) {
        // set body if appropriate
        if (body != null && request instanceof HttpEntityEnclosingRequestBase) {
            byte[] bodyBytes = null;
            try {
                bodyBytes = CBLServer.getObjectMapper().writeValueAsBytes(body);
            } catch (Exception e) {
                Log.e(CBLDatabase.TAG, "Error serializing body of request", e);
            }
            ByteArrayEntity entity = new ByteArrayEntity(bodyBytes);
            entity.setContentType("application/json");
            ((HttpEntityEnclosingRequestBase) request).setEntity(entity);
        }
    }

    protected void executeRequest(HttpClient httpClient, HttpUriRequest request) {
        Object fullBody = null;
        Throwable error = null;
        try {
            HttpResponse response = httpClient.execute(request, httpContext);

            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() >= 300) {
                Log.e(CBLDatabase.TAG,
                        "Got error " + Integer.toString(status.getStatusCode()));
                Log.e(CBLDatabase.TAG, "Request was for: " + request.toString());
                Log.e(CBLDatabase.TAG,
                        "Status reason: " + status.getReasonPhrase());
                error = new HttpResponseException(status.getStatusCode(),
                        status.getReasonPhrase());
            } else {
                HttpEntity temp = response.getEntity();
                if (temp != null) {
                    try {
                        InputStream stream = temp.getContent();
                        fullBody = CBLServer.getObjectMapper().readValue(stream,
                                Object.class);
                    } finally {
                        try {
                            temp.consumeContent();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        } catch (ClientProtocolException e) {
            Log.e(CBLDatabase.TAG, "client protocol exception", e);
            error = e;
        } catch (IOException e) {
            Log.e(CBLDatabase.TAG, "io exception", e);
            error = e;
        }
        respondWithResult(fullBody, error);
    }

    protected void preemptivelySetAuthCredentials(HttpClient httpClient) {
        // if the URL contains user info AND if this a DefaultHttpClient
        // then preemptively set the auth credentials
        if (url.getUserInfo() != null) {
            if (url.getUserInfo().contains(":") && !url.getUserInfo().trim().equals(":")) {
                String[] userInfoSplit = url.getUserInfo().split(":");
                final Credentials creds = new UsernamePasswordCredentials(
                        userInfoSplit[0], userInfoSplit[1]);
                if (httpClient instanceof DefaultHttpClient) {
                    DefaultHttpClient dhc = (DefaultHttpClient) httpClient;

                    HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {

                        @Override
                        public void process(HttpRequest request,
                                HttpContext context) throws HttpException,
                                IOException {
                            AuthState authState = (AuthState) context
                                    .getAttribute(ClientContext.TARGET_AUTH_STATE);
                            CredentialsProvider credsProvider = (CredentialsProvider) context
                                    .getAttribute(ClientContext.CREDS_PROVIDER);
                            HttpHost targetHost = (HttpHost) context
                                    .getAttribute(ExecutionContext.HTTP_TARGET_HOST);

                            if (authState.getAuthScheme() == null) {
                                AuthScope authScope = new AuthScope(
                                        targetHost.getHostName(),
                                        targetHost.getPort());
                                authState.setAuthScheme(new BasicScheme());
                                authState.setCredentials(creds);
                            }
                        }
                    };

                    dhc.addRequestInterceptor(preemptiveAuth, 0);
                }
            } else {
                Log.w(CBLDatabase.TAG,
                        "CBLRemoteRequest Unable to parse user info, not setting credentials");
            }
        }
    }

    public void respondWithResult(final Object result, final Throwable error) {
        if (workExecutor != null) {
            workExecutor.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        onCompletion.onCompletion(result, error);
                    } catch (Exception e) {
                        // don't let this crash the thread
                        Log.e(CBLDatabase.TAG,
                                "CBLRemoteRequestCompletionBlock throw Exception",
                                e);
                    }
                }
            });
        } else {
            Log.e(CBLDatabase.TAG, "work executor was null!!!");
        }
    }

}
