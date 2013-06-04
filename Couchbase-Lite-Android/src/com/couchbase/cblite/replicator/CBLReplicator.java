package com.couchbase.cblite.replicator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.Handler;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLMisc;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLRevisionList;
import com.couchbase.cblite.support.HttpClientFactory;
import com.couchbase.cblite.support.CBLBatchProcessor;
import com.couchbase.cblite.support.CBLBatcher;
import com.couchbase.cblite.support.CBLRemoteRequest;
import com.couchbase.cblite.support.CBLRemoteRequestCompletionBlock;

public abstract class CBLReplicator extends Observable {

    private static int lastSessionID = 0;

    protected ScheduledExecutorService workExecutor;
    protected CBLDatabase db;
    protected URL remote;
    protected boolean continuous;
    protected String lastSequence;
    protected boolean lastSequenceChanged;
    protected Map<String,Object> remoteCheckpoint;
    protected boolean savingCheckpoint;
    protected boolean overdueForSave;
    protected boolean running;
    protected boolean active;
    protected Throwable error;
    protected String sessionID;
    protected CBLBatcher<CBLRevision> batcher;
    protected int asyncTaskCount;
    private int changesProcessed;
    private int changesTotal;
    protected final HttpClientFactory clientFacotry;
    protected String filterName;
    protected Map<String,Object> filterParams;
    protected ExecutorService remoteRequestExecutor;

    protected static final int PROCESSOR_DELAY = 500;
    protected static final int INBOX_CAPACITY = 100;

    public CBLReplicator(CBLDatabase db, URL remote, boolean continuous, ScheduledExecutorService workExecutor) {
        this(db, remote, continuous, null, workExecutor);
    }

    public CBLReplicator(CBLDatabase db, URL remote, boolean continuous, HttpClientFactory clientFacotry, ScheduledExecutorService workExecutor) {

        this.db = db;
        this.remote = remote;
        this.continuous = continuous;
        this.workExecutor = workExecutor;

        this.remoteRequestExecutor = Executors.newCachedThreadPool();


        batcher = new CBLBatcher<CBLRevision>(workExecutor, INBOX_CAPACITY, PROCESSOR_DELAY, new CBLBatchProcessor<CBLRevision>() {
            @Override
            public void process(List<CBLRevision> inbox) {
                Log.v(CBLDatabase.TAG, "*** " + toString() + ": BEGIN processInbox (" + inbox.size() + " sequences)");
                processInbox(new CBLRevisionList(inbox));
                Log.v(CBLDatabase.TAG, "*** " + toString() + ": END processInbox (lastSequence=" + lastSequence);
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

    public String getFilterName() {
        return filterName;
    }
    
    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }
    
    public Map<String,Object> getFilterParams() {
        return filterParams;
    }

    public void setFilterParams(Map<String, Object> filterParams) {
        this.filterParams = filterParams;
    }
    
    public boolean isContinuous() {
        return continuous;
    }
    
    public void setContinuous(boolean continuous) {
        if (!isRunning()) {
            this.continuous = continuous;
        }
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
        String maskedRemoteWithoutCredentials = (remote != null ? remote.toExternalForm() : "");
        maskedRemoteWithoutCredentials = maskedRemoteWithoutCredentials.replaceAll("://.*:.*@","://---:---@");
        String name = getClass().getSimpleName() + "[" + maskedRemoteWithoutCredentials + "]";
        return name;
    }

    public boolean isPush() {
        return false;
    }

    public String getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(String lastSequenceIn) {
        if(!lastSequenceIn.equals(lastSequence)) {
            Log.v(CBLDatabase.TAG, toString() + ": Setting lastSequence to " + lastSequenceIn + " from( " + lastSequence + ")");
            lastSequence = lastSequenceIn;
            if(!lastSequenceChanged) {
                lastSequenceChanged = true;
                workExecutor.schedule(new Runnable() {

                    @Override
                    public void run() {
                        saveLastSequence();
                    }
                }, 2 * 1000, TimeUnit.MILLISECONDS);
            }
        }
    }

    public int getChangesProcessed() {
        return changesProcessed;
    }

    public void setChangesProcessed(int processed) {
        this.changesProcessed = processed;
        setChanged();
        notifyObservers();
    }

    public int getChangesTotal() {
        return changesTotal;
    }

    public void setChangesTotal(int total) {
        this.changesTotal = total;
        setChanged();
        notifyObservers();
    }

    public String getSessionID() {
        return sessionID;
    }

    public void start() {
        if(running) {
            return;
        }
        this.sessionID = String.format("repl%03d", ++lastSessionID);
        Log.v(CBLDatabase.TAG, toString() + " STARTING ...");
        running = true;
        lastSequence = null;

        fetchRemoteCheckpointDoc();
    }

    public abstract void beginReplicating();

    public void stop() {
        if(!running) {
            return;
        }
        Log.v(CBLDatabase.TAG, toString() + " STOPPING...");
        batcher.flush();
        continuous = false;
        if(asyncTaskCount == 0) {
            stopped();
        }
    }

    public void stopped() {
        Log.v(CBLDatabase.TAG, toString() + " STOPPED");
        running = false;
        this.changesProcessed = this.changesTotal = 0;

        saveLastSequence();
        notifyObservers();
        
        batcher = null;
        db = null;
    }

    public synchronized void asyncTaskStarted() {
        ++asyncTaskCount;
    }

    public synchronized void asyncTaskFinished(int numTasks) {
        this.asyncTaskCount -= numTasks;
        if(asyncTaskCount == 0) {
            if(!continuous) {
                stopped();
            }
        }
    }

    public void addToInbox(CBLRevision rev) {
        if(batcher.count() == 0) {
            active = true;
        }
        batcher.queueObject(rev);
        //Log.v(CBLDatabase.TAG, String.format("%s: Received #%d %s", toString(), rev.getSequence(), rev.toString()));
    }

    public void processInbox(CBLRevisionList inbox) {

    }

    public void sendAsyncRequest(String method, String relativePath, Object body, CBLRemoteRequestCompletionBlock onCompletion) {
        //Log.v(CBLDatabase.TAG, String.format("%s: %s .%s", toString(), method, relativePath));
        String urlStr = remote.toExternalForm() + relativePath;
        try {
            URL url = new URL(urlStr);
            CBLRemoteRequest request = new CBLRemoteRequest(workExecutor, clientFacotry, method, url, body, onCompletion);
            remoteRequestExecutor.execute(request);
        } catch (MalformedURLException e) {
            Log.e(CBLDatabase.TAG, "Malformed URL for async request", e);
        }
    }

    /** CHECKPOINT STORAGE: **/

    public void maybeCreateRemoteDB() {
        // CBLPusher overrides this to implement the .createTarget option
    }

    /**
     * This is the _local document ID stored on the remote server to keep track of state.
     * Its ID is based on the local database ID (the private one, to make the result unguessable)
     * and the remote database's URL.
     */
    public String remoteCheckpointDocID() {
        if(db == null) {
            return null;
        }
        String input = db.privateUUID() + "\n" + remote.toExternalForm() + "\n" + (isPush() ? "1" : "0");
        return CBLMisc.TDHexSHA1Digest(input.getBytes());
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
        sendAsyncRequest("GET", "/_local/" + remoteCheckpointDocID(), null, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if(e != null && e instanceof HttpResponseException && ((HttpResponseException)e).getStatusCode() != 404) {
                    error = e;
                } else {
                    if(e instanceof HttpResponseException && ((HttpResponseException)e).getStatusCode() == 404) {
                        maybeCreateRemoteDB();
                    }
                    Map<String,Object> response = (Map<String,Object>)result;
                    remoteCheckpoint = response;
                    String remoteLastSequence = null;
                    if(response != null) {
                        remoteLastSequence = (String)response.get("lastSequence");
                    }
                    if(remoteLastSequence != null && remoteLastSequence.equals(localLastSequence)) {
                        lastSequence = localLastSequence;
                        Log.v(CBLDatabase.TAG, this + ": Replicating from lastSequence=" + lastSequence);
                    } else {
                        Log.v(CBLDatabase.TAG, this + ": lastSequence mismatch: I had " + localLastSequence + ", remote had " + remoteLastSequence);
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
        if (savingCheckpoint) {
            // If a save is already in progress, don't do anything. (The completion block will trigger
            // another save after the first one finishes.)
            overdueForSave = true;
            return;
        }

        lastSequenceChanged = false;
        overdueForSave = false;

        Log.v(CBLDatabase.TAG, this + " checkpointing sequence=" + lastSequence);
        final Map<String,Object> body = new HashMap<String,Object>();
        if(remoteCheckpoint != null) {
            body.putAll(remoteCheckpoint);
        }
        body.put("lastSequence", lastSequence);

        String remoteCheckpointDocID = remoteCheckpointDocID();
        if(remoteCheckpointDocID == null) {
            return;
        }
        savingCheckpoint = true;
        sendAsyncRequest("PUT", "/_local/" + remoteCheckpointDocID, body, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
            	savingCheckpoint = false;
                if(e != null) {
                    Log.v(CBLDatabase.TAG, this + ": Unable to save remote checkpoint", e);
                    // TODO: If error is 401 or 403, and this is a pull, remember that remote is read-only and don't attempt to read its checkpoint next time.
                } else {
                    Map<String,Object> response = (Map<String,Object>)result;
                    body.put("_rev", response.get("rev"));
                    remoteCheckpoint = body;
                }
                if (overdueForSave) {
                	saveLastSequence();
                }
            }

        });
        db.setLastSequence(lastSequence, remote, isPush());
    }

}
