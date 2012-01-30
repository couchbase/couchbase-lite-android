package com.couchbase.touchdb.replicator.changetracker;

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
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.codehaus.jackson.map.ObjectMapper;

import android.os.Handler;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;

/**
 * Reads the continuous-mode _changes feed of a database, and sends the
 * individual change entries to its client's changeTrackerReceivedChange()
 */
public class TDChangeTracker implements Runnable {

    private URL databaseURL;
    private TDChangeTrackerClient client;
    private TDChangeTrackerMode mode;
    private Object lastSequenceID;

    private Handler handler;
    private Thread thread;
    private boolean running = false;

    private String filterName;
    private Map<String, Object> filterParams;

    public enum TDChangeTrackerMode {
        OneShot, LongPoll, Continuous
    }

    public TDChangeTracker(URL databaseURL, TDChangeTrackerMode mode,
            Object lastSequenceID, TDChangeTrackerClient client) {
        this.handler = new Handler();
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

    public void setClient(TDChangeTrackerClient client) {
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
            path += "longpoll";
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
                    path += URLEncoder.encode(filterParamKey) + "=" + URLEncoder.encode(filterParams.get(filterParamKey).toString());
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
            Log.e(TDDatabase.TAG, "Changes feed ULR is malformed", e);
        }
        return result;
    }

    @Override
    public void run() {
        running = true;
        HttpClient httpClient = client.getHttpClient();
        while (running) {
            HttpUriRequest request = new HttpGet(getChangesFeedURL().toString());
            try {
                Log.v(TDDatabase.TAG, "Making request to " + getChangesFeedURL().toString());
                HttpResponse response = httpClient.execute(request);
                StatusLine status = response.getStatusLine();
                if(status.getStatusCode() >= 300) {
                    Log.e(TDDatabase.TAG, "Change tracker got error " + Integer.toString(status.getStatusCode()));
                    stopped();
                }
                HttpEntity entity = response.getEntity();
                if(entity != null) {
                	try {
	                    InputStream stream = entity.getContent();
	                    if(mode != TDChangeTrackerMode.Continuous) {
	                        ObjectMapper mapper = new ObjectMapper();
	                        Map<String,Object> fullBody = mapper.readValue(stream, Map.class);
	                        boolean responseOK = receivedPollResponse(fullBody);
	                        if(mode == TDChangeTrackerMode.LongPoll && responseOK) {
	                            Log.v(TDDatabase.TAG, "Starting new longpoll");
	                            continue;
	                        } else {
	                            stop();
	                        }
	                    }
	                    else {
	                        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
	                        String line = null;
	                        while ((line=reader.readLine()) != null) {
	                            receivedChunk(line);
	                        }
	                    }
                	} finally {
                		try { entity.consumeContent(); } catch (IOException e){}
                	}
                }
            } catch (ClientProtocolException e) {
                Log.e(TDDatabase.TAG, "ClientProtocolException in change tracker", e);
            } catch (IOException e) {
                Log.e(TDDatabase.TAG, "IOException in change tracker", e);
            }
        }
        Log.v(TDDatabase.TAG, "Chagne tracker run loop exiting");
    }

    public void receivedChunk(String line) {
        if(line.length() <= 1) {
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String,Object> change = (Map)mapper.readValue(line, Map.class);
            receivedChange(change);
        } catch (Exception e) {
            Log.w(TDDatabase.TAG, "Exception parsing JSON in change tracker", e);
        }
    }

    public boolean receivedChange(final Map<String,Object> change) {
        Object seq = change.get("seq");
        if(seq == null) {
            return false;
        }
        //pass the change to the client on the thread that created this change tracker
        handler.post(new Runnable() {

            TDChangeTrackerClient copy = client;

            @Override
            public void run() {
                if(copy == null) {
                    Log.v(TDDatabase.TAG, "cannot notify client, client is null");
                } else {
                    Log.v(TDDatabase.TAG, "about to notify client");
                    copy.changeTrackerReceivedChange(change);
                }
            }
        });
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

    public boolean start() {
        thread = new Thread(this);
        thread.start();
        return true;
    }

    public void stop() {
        running = false;
        stopped();
    }

    public void stopped() {
        if (client != null) {
            handler.post(new Runnable() {

                TDChangeTrackerClient copy = client;

                @Override
                public void run() {
                    copy.changeTrackerStopped(TDChangeTracker.this);
                }
            });
        }
        client = null;
    }

}
