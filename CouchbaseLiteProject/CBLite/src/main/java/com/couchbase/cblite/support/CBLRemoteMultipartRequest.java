package com.couchbase.cblite.support;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;

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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class CBLRemoteMultipartRequest implements Runnable {

    private ScheduledExecutorService workExecutor;
    private final HttpClientFactory clientFactory;
    private String method;
    private URL url;
    private MultipartEntity multiPart;
    private CBLRemoteRequestCompletionBlock onCompletion;
    private BasicHttpContext httpContext;

    public CBLRemoteMultipartRequest(ScheduledExecutorService workExecutor,
                            HttpClientFactory clientFactory, String method, URL url,
                            MultipartEntity multiPart, CBLRemoteRequestCompletionBlock onCompletion,
                            BasicHttpContext httpContext) {
        this.clientFactory = clientFactory;
        this.method = method;
        this.url = url;
        this.multiPart = multiPart;
        this.onCompletion = onCompletion;
        this.workExecutor = workExecutor;
        this.httpContext = httpContext;
    }

    @Override
    public void run() {
        HttpClient httpClient = clientFactory.getHttpClient();

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


        HttpUriRequest request = null;
        if (method.equalsIgnoreCase("PUT")) {
            HttpPut putRequest = new HttpPut(url.toExternalForm());
            putRequest.setEntity(multiPart);
            request = putRequest;

        } else if (method.equalsIgnoreCase("POST")) {
            HttpPost postRequest = new HttpPost(url.toExternalForm());
            postRequest.setEntity(multiPart);
            request = postRequest;
        } else {
            throw new IllegalArgumentException("Invalid request method: " + method);
        }

        request.addHeader("Accept", "*/*");

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
