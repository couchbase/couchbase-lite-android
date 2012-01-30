package com.couchbase.touchdb.replicator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.Handler;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDMisc;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.support.HttpClientFactory;
import com.couchbase.touchdb.support.TDBatchProcessor;
import com.couchbase.touchdb.support.TDBatcher;
import com.couchbase.touchdb.support.TDRemoteRequest;
import com.couchbase.touchdb.support.TDRemoteRequestCompletionBlock;

public abstract class TDReplicator extends Observable {

    private static int lastSessionID = 0;

    protected Handler handler;
    protected TDDatabase db;
    protected URL remote;
    protected boolean continuous;
    protected String lastSequence;
    protected boolean lastSequenceChanged;
    protected Map<String,Object> remoteCheckpoint;
    protected boolean running;
    protected boolean active;
    protected Throwable error;
    protected String sessionID;
    protected TDBatcher<TDRevision> batcher;
    protected int asyncTaskCount;
    protected int changesProcessed;
    protected int changesTotal;
    protected final HttpClientFactory clientFacotry;

    protected static final int PROCESSOR_DELAY = 500;
    protected static final int INBOX_CAPACITY = 100;

    public TDReplicator(TDDatabase db, URL remote, boolean continuous) {
        this(db, remote, continuous, null);
    }
    
    public TDReplicator(TDDatabase db, URL remote, boolean continuous, HttpClientFactory clientFacotry) {
        this.handler = new Handler();
        this.db = db;
        this.remote = remote;
        this.continuous = continuous;

        batcher = new TDBatcher<TDRevision>(INBOX_CAPACITY, PROCESSOR_DELAY, new TDBatchProcessor<TDRevision>() {
            @Override
            public void process(List<TDRevision> inbox) {
                Log.v(TDDatabase.TAG, "*** " + toString() + ": BEGIN processInbox (" + inbox.size() + " sequences)");
                processInbox(inbox);
                Log.v(TDDatabase.TAG, "*** " + toString() + ": END processInbox (lastSequence=" + lastSequence);
                active = false;
            }
        });
        
        this.clientFacotry = clientFacotry != null ? clientFacotry : new HttpClientFactory() {
			@Override
			public HttpClient getHttpClient() {
				return new DefaultHttpClient();
			}
		};
    }

    public boolean isRunning() {
        return running;
    }

    public URL getRemote() {
        return remote;
    }

    public void databaseClosing() {
        saveLastSequence();
        stop();
        db = null;
    }

    public String toString() {
        return getClass().getName() + "[" + remote.toExternalForm() + "]";
    }

    public boolean isPush() {
        return false;
    }

    public String getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(String lastSequenceIn) {
        if(!lastSequenceIn.equals(lastSequence)) {
            Log.v(TDDatabase.TAG, toString() + ": Setting lastSequence to " + lastSequenceIn + " from( " + lastSequence + ")");
            lastSequence = lastSequenceIn;
            if(!lastSequenceChanged) {
                lastSequenceChanged = true;
                handler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        saveLastSequence();
                    }
                }, 2 * 1000);
            }
        }
    }

    public void setChangesProcessed(int processed) {
        this.changesProcessed = processed;
        setChanged();
        notifyObservers();
    }

    public void setChangesTotal(int total) {
        this.changesTotal = total;
        setChanged();
        notifyObservers();
    }

    public void start() {
        if(running) {
            return;
        }
        this.sessionID = String.format("repl%03d", ++lastSessionID);
        Log.v(TDDatabase.TAG, toString() + " STARTING ...");
        running = true;
        lastSequence = null;

        fetchRemoteCheckpointDoc();
    }

    public abstract void beginReplicating();

    public void stop() {
        if(!running) {
            return;
        }
        Log.v(TDDatabase.TAG, toString() + " STOPPING...");
        batcher.flush();
        if(asyncTaskCount == 0) {
            stopped();
        }
    }

    public void stopped() {
        Log.v(TDDatabase.TAG, toString() + " STOPPED");
        running = false;
        this.changesProcessed = this.changesTotal = 0;
        db.replicatorDidStop(this);
    }

    public void asyncTaskStarted() {
        ++asyncTaskCount;
    }

    public void asyncTaskFinished(int numTasks) {
        this.asyncTaskCount -= numTasks;
        if(asyncTaskCount == 0) {
            stopped();
        }
    }

    public void addToInbox(TDRevision rev) {
        if(batcher.count() == 0) {
            active = true;
        }
        batcher.queueObject(rev);
        Log.v(TDDatabase.TAG, String.format("%s: Received #%lld %s", toString(), rev.getSequence(), rev.toString()));
    }

    public void processInbox(List<TDRevision> inbox) {

    }

    public void sendAsyncRequest(String method, String relativePath, Object body, TDRemoteRequestCompletionBlock onCompletion) {
        Log.v(TDDatabase.TAG, String.format("%s: %s .%s", toString(), method, relativePath));
        String urlStr = remote.toExternalForm() + relativePath;
        try {
            URL url = new URL(urlStr);
            TDRemoteRequest request = new TDRemoteRequest(clientFacotry, method, url, body, onCompletion);
            request.start();
        } catch (MalformedURLException e) {
            Log.e(TDDatabase.TAG, "Malformed URL for async request", e);
        }
    }

    /** CHECKPOINT STORAGE: **/

    public void maybeCreateRemoteDB() {
        // TDPusher overrides this to implement the .createTarget option
    }

    /**
     * This is the _local document ID stored on the remote server to keep track of state.
     * Its ID is based on the local database ID (the private one, to make the result unguessable)
     * and the remote database's URL.
     */
    public String remoteCheckpointDocID() {
        String input = String.format("%s\n%s\n%d", db.privateUUID(), remote.toExternalForm(), (isPush() ? 1 : 0));
        return TDMisc.TDHexSHA1Digest(input.getBytes());
    }

    public void fetchRemoteCheckpointDoc() {
        lastSequenceChanged = false;
        final String localLastSequence = db.lastSequenceWithRemoteURL(remote, isPush());
        if(localLastSequence == null) {
            maybeCreateRemoteDB();
            beginReplicating();
            return;
        }

        asyncTaskStarted();
        sendAsyncRequest("GET", "/_local/" + remoteCheckpointDocID(), null, new TDRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if(e != null && e instanceof HttpResponseException && ((HttpResponseException)e).getStatusCode() != 404) {
                    error = e;
                } else {
                    if(e instanceof HttpResponseException && ((HttpResponseException)e).getStatusCode() != 404) {
                        maybeCreateRemoteDB();
                    }
                    Map<String,Object> response = (Map<String,Object>)result;
                    remoteCheckpoint = response;
                    String remoteLastSequence = (String)response.get("lastSequence");
                    if(remoteLastSequence.equals(localLastSequence)) {
                        lastSequence = localLastSequence;
                        Log.v(TDDatabase.TAG, String.format("%s: Replicating from lastSequence=%s", this, lastSequence));
                    } else {
                        Log.v(TDDatabase.TAG, String.format("%s: lastSequence mismatch: I had %s, remote had %s", this, localLastSequence, remoteLastSequence));
                    }
                    beginReplicating();
                }
                asyncTaskFinished(1);
            }

        });
    }

    public void saveLastSequence() {
        if(!lastSequenceChanged) {
            return;
        }
        lastSequenceChanged = false;
        Log.v(TDDatabase.TAG, String.format("%s checkpointing sequence=%s", this, lastSequence));
        final Map<String,Object> body = new HashMap<String,Object>();
        if(remoteCheckpoint != null) {
            body.putAll(remoteCheckpoint);
        }
        body.put("lastSequence", lastSequence);
        sendAsyncRequest("PUT", "/_local/" + remoteCheckpointDocID(), body, new TDRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if(error != null) {
                    Log.v(TDDatabase.TAG, String.format("%s: Unable to save remote checkpoint: %s", this, e), e);
                    // TODO: If error is 401 or 403, and this is a pull, remember that remote is read-only and don't attempt to read its checkpoint next time.
                } else {
                    Map<String,Object> response = (Map<String,Object>)result;
                    body.put("_rev", response.get("rev"));
                    remoteCheckpoint = body;
                }
            }

        });
        db.setLastSequence(lastSequence, remote, isPush());
    }

}
