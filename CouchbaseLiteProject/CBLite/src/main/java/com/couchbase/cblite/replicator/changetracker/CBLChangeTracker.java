package com.couchbase.cblite.replicator.changetracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLServer;

/**
 * Reads the continuous-mode _changes feed of a database, and sends the
 * individual change entries to its client's changeTrackerReceivedChange()
 */
public class CBLChangeTracker implements Runnable {

    private URL databaseURL;
    private CBLChangeTrackerClient client;
    private TDChangeTrackerMode mode;
    private Object lastSequenceID;

    private Thread thread;
    private boolean running = false;
    private HttpUriRequest request;

    private String filterName;
    private Map<String, Object> filterParams;

    private Throwable error;

    public enum TDChangeTrackerMode {
        OneShot, LongPoll, Continuous
    }

    public CBLChangeTracker(URL databaseURL, TDChangeTrackerMode mode,
            Object lastSequenceID, CBLChangeTrackerClient client) {
        this.databaseURL = databaseURL;
        this.mode = mode;
        this.lastSequenceID = lastSequenceID;
        this.client = client;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public void setFilterParams(Map<String, Object> filterParams) {
        this.filterParams = filterParams;
    }

    public void setClient(CBLChangeTrackerClient client) {
        this.client = client;
    }

    public String getDatabaseName() {
        String result = null;
        if (databaseURL != null) {
            result = databaseURL.getPath();
            if (result != null) {
                int pathLastSlashPos = result.lastIndexOf('/');
                if (pathLastSlashPos > 0) {
                    result = result.substring(pathLastSlashPos);
                }
            }
        }
        return result;
    }

    public String getChangesFeedPath() {
        String path = "_changes?feed=";
        switch (mode) {
        case OneShot:
            path += "normal";
            break;
        case LongPoll:
            path += "longpoll&limit=50";
            break;
        case Continuous:
            path += "continuous";
            break;
        }
        path += "&heartbeat=300000";

        if(lastSequenceID != null) {
            path += "&since=" + URLEncoder.encode(lastSequenceID.toString());
        }
        if(filterName != null) {
            path += "&filter=" + URLEncoder.encode(filterName);
            if(filterParams != null) {
                for (String filterParamKey : filterParams.keySet()) {
                    path += "&" + URLEncoder.encode(filterParamKey) + "=" + URLEncoder.encode(filterParams.get(filterParamKey).toString());
                }
            }
        }

        return path;
    }

    public URL getChangesFeedURL() {
        String dbURLString = databaseURL.toExternalForm();
        if(!dbURLString.endsWith("/")) {
            dbURLString += "/";
        }
        dbURLString += getChangesFeedPath();
        URL result = null;
        try {
            result = new URL(dbURLString);
        } catch(MalformedURLException e) {
            Log.e(CBLDatabase.TAG, "Changes feed ULR is malformed", e);
        }
        return result;
    }

    @Override
    public void run() {
        running = true;
        HttpClient httpClient = client.getHttpClient();
        while (running) {

            URL url = getChangesFeedURL();
            request = new HttpGet(url.toString());

            // if the URL contains user info AND if this a DefaultHttpClient
            // then preemptively set the auth credentials
            if(url.getUserInfo() != null) {
                Log.v(CBLDatabase.TAG, "url.getUserInfo(): " + url.getUserInfo());
                if(url.getUserInfo().contains(":") && !url.getUserInfo().trim().equals(":")) {
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
                    Log.w(CBLDatabase.TAG, "ChangeTracker Unable to parse user info, not setting credentials");
                }
            }

            try {
                String maskedRemoteWithoutCredentials = getChangesFeedURL().toString();
                maskedRemoteWithoutCredentials = maskedRemoteWithoutCredentials.replaceAll("://.*:.*@","://---:---@");
                Log.v(CBLDatabase.TAG, "Making request to " + maskedRemoteWithoutCredentials);
                HttpResponse response = httpClient.execute(request);
                StatusLine status = response.getStatusLine();
                if(status.getStatusCode() >= 300) {
                    Log.e(CBLDatabase.TAG, "Change tracker got error " + Integer.toString(status.getStatusCode()));
                    stop();
                }
                HttpEntity entity = response.getEntity();
                if(entity != null) {
                	try {
	                    InputStream input = entity.getContent();
	                    if(mode == TDChangeTrackerMode.LongPoll) {
	                        Map<String,Object> fullBody = CBLServer.getObjectMapper().readValue(input, Map.class);
	                        boolean responseOK = receivedPollResponse(fullBody);
	                        if(mode == TDChangeTrackerMode.LongPoll && responseOK) {
	                            Log.v(CBLDatabase.TAG, "Starting new longpoll");
	                            continue;
	                        } else {
	                            Log.w(CBLDatabase.TAG, "Change tracker calling stop");
	                            stop();
	                        }
	                    }
	                    else {
	                        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
	                        String line = null;
	                        while ((line=reader.readLine()) != null) {
	                            //skip over lines which may be in a non-continuous response
	                            if(line.equals("{\"results\":[") || line.equals("],")) {
	                                continue;
	                            }
	                            else if(line.startsWith("\"last_seq\"") && mode == TDChangeTrackerMode.OneShot) {
	                                Log.w(CBLDatabase.TAG, "Change tracker calling stop");
	                                stop();
	                                break;
	                            }
	                            receivedChunk(line);
	                        }
	                        Log.v(CBLDatabase.TAG, "read null from inpustream continuing");
	                    }
                	} finally {
                		try { entity.consumeContent(); } catch (IOException e){}
                	}
                }
            } catch (ClientProtocolException e) {
                Log.e(CBLDatabase.TAG, "ClientProtocolException in change tracker", e);
            } catch (IOException e) {

                if(running) {
                    //we get an exception when we're shutting down and have to
                    //close the socket underneath our read, ignore that
                    Log.e(CBLDatabase.TAG, "IOException in change tracker", e);
                }
            }
        }
        Log.v(CBLDatabase.TAG, "Change tracker run loop exiting");
    }

    public boolean receivedChunk(String line) {
        if(line.length() > 1) {
            try {
                Map<String,Object> change = (Map)CBLServer.getObjectMapper().readValue(line, Map.class);
                if(!receivedChange(change)) {
                    Log.w(CBLDatabase.TAG, String.format("Received unparseable change line from server: %s", line));
                    return false;
                }
            } catch (Exception e) {
                Log.w(CBLDatabase.TAG, "Exception parsing JSON in change tracker", e);
                return false;
            }
        }
        return true;
    }

    public boolean receivedChange(final Map<String,Object> change) {
        Object seq = change.get("seq");
        if(seq == null) {
            return false;
        }
        //pass the change to the client on the thread that created this change tracker
        if(client != null) {
            client.changeTrackerReceivedChange(change);
        }
        lastSequenceID = seq;
        return true;
    }

    public boolean receivedPollResponse(Map<String,Object> response) {
        List<Map<String,Object>> changes = (List)response.get("results");
        if(changes == null) {
            return false;
        }
        for (Map<String,Object> change : changes) {
            if(!receivedChange(change)) {
                return false;
            }
        }
        return true;
    }

    public void setUpstreamError(String message) {
        Log.w(CBLDatabase.TAG, String.format("Server error: %s", message));
        this.error = new Throwable(message);
    }

    public boolean start() {
        this.error = null;
        thread = new Thread(this, "ChangeTracker-" + databaseURL.toExternalForm());
        thread.start();
        return true;
    }

    public void stop() {
        Log.d(CBLDatabase.TAG, "changed tracker asked to stop");
        running = false;
        thread.interrupt();
        if(request != null) {
            request.abort();
        }

        stopped();
    }

    public void stopped() {
        Log.d(CBLDatabase.TAG, "change tracker in stopped");
        if (client != null) {
            Log.d(CBLDatabase.TAG, "posting stopped");
            client.changeTrackerStopped(CBLChangeTracker.this);
        }
        client = null;
        Log.d(CBLDatabase.TAG, "change tracker client should be null now");
    }

    public boolean isRunning() {
        return running;
    }

}
