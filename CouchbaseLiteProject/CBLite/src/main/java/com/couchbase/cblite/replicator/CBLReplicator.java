package com.couchbase.cblite.replicator;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;

import android.net.Uri;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLMisc;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLRevisionList;
import com.couchbase.cblite.auth.CBLAuthorizer;
import com.couchbase.cblite.auth.CBLPersonaAuthorizer;
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
    protected Map<String, Object> remoteCheckpoint;
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
    protected Map<String, Object> filterParams;
    protected ExecutorService remoteRequestExecutor;
    protected CBLAuthorizer authorizer;
    protected BasicHttpContext httpContext;

    protected static final int PROCESSOR_DELAY = 500;
    protected static final int INBOX_CAPACITY = 100;

    public CBLReplicator(CBLDatabase db, URL remote, boolean continuous, ScheduledExecutorService workExecutor) {
        this(db, remote, continuous, null, workExecutor);
    }

    public CBLReplicator(CBLDatabase db, URL remote, boolean continuous, HttpClientFactory clientFacotry, ScheduledExecutorService workExecutor) {

        this.db = db;
        this.continuous = continuous;
        this.workExecutor = workExecutor;
        this.remote = remote;
        this.remoteRequestExecutor = Executors.newCachedThreadPool();

        if (remote.getQuery() != null && !remote.getQuery().isEmpty()) {

            Uri uri = Uri.parse(remote.toExternalForm());

            String personaAssertion = uri.getQueryParameter(CBLPersonaAuthorizer.QUERY_PARAMETER);
            if (personaAssertion != null && !personaAssertion.isEmpty()) {
                String email = CBLPersonaAuthorizer.registerAssertion(personaAssertion);
                CBLPersonaAuthorizer authorizer = new CBLPersonaAuthorizer(email);
                setAuthorizer(authorizer);
            }

            // we need to remove the query from the URL, since it will cause problems when
            // communicating with sync gw / couchdb
            try {
                this.remote = new URL(remote.getProtocol(), remote.getHost(), remote.getPort(), remote.getPath());
            } catch (MalformedURLException e) {
                Log.e(CBLDatabase.TAG, "Exception trying to rebuild url without query parameters.  remote: " + remote);
            }
            Log.d(CBLDatabase.TAG, "new remote url: " + remote);

        }

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

        httpContext = new BasicHttpContext();
        httpContext.setAttribute(ClientContext.COOKIE_STORE, new BasicCookieStore());

    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public Map<String, Object> getFilterParams() {
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

    public void setAuthorizer(CBLAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public CBLAuthorizer getAuthorizer() {
        return authorizer;
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
        maskedRemoteWithoutCredentials = maskedRemoteWithoutCredentials.replaceAll("://.*:.*@", "://---:---@");
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
        if (!lastSequenceIn.equals(lastSequence)) {
            Log.v(CBLDatabase.TAG, toString() + ": Setting lastSequence to " + lastSequenceIn + " from( " + lastSequence + ")");
            lastSequence = lastSequenceIn;
            if (!lastSequenceChanged) {
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
        if (running) {
            return;
        }
        this.sessionID = String.format("repl%03d", ++lastSessionID);
        Log.v(CBLDatabase.TAG, toString() + " STARTING ...");
        running = true;
        lastSequence = null;

        checkSession();
    }

    protected void checkSession() {
        if (getAuthorizer() != null && getAuthorizer().usesCookieBasedLogin()) {
            checkSessionAtPath("/_session");
        } else {
            fetchRemoteCheckpointDoc();
        }
    }

    protected void checkSessionAtPath(final String sessionPath) {

        Log.d(CBLDatabase.TAG, String.format("%s checkSessionAtPath:: %s", this, sessionPath));

        asyncTaskStarted();
        sendAsyncRequest("GET", sessionPath, null, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {

                Log.d(CBLDatabase.TAG, String.format("%s checkSessionAtPath.onCompletion(): %s", this, sessionPath));

                if (e instanceof HttpResponseException &&
                        ((HttpResponseException) e).getStatusCode() == 404 &&
                        sessionPath.equalsIgnoreCase("/_session")) {

                    Log.d(CBLDatabase.TAG, String.format("%s checkSessionAtPath got 404 for %s, calling checkSessionAtPath with _session", this, sessionPath));
                    checkSessionAtPath("_session");
                    return;
                } else {
                    Map<String, Object> response = (Map<String, Object>) result;
                    Map<String, Object> userCtx = (Map<String, Object>) response.get("userCtx");
                    String username = (String) userCtx.get("name");
                    if (username != null && username.length() > 0) {
                        Log.d(CBLDatabase.TAG, String.format("%s Active session, logged in as %s", this, username));
                        fetchRemoteCheckpointDoc();
                    } else {
                        Log.d(CBLDatabase.TAG, String.format("%s No active session, going to login", this));
                        login();
                    }

                }
                asyncTaskFinished(1);
            }

        });
    }

    public abstract void beginReplicating();

    public void stop() {
        if (!running) {
            return;
        }
        Log.v(CBLDatabase.TAG, toString() + " STOPPING...");
        batcher.flush();
        continuous = false;
        if (asyncTaskCount == 0) {
            stopped();
        }
    }

    public void stopped() {
        Log.v(CBLDatabase.TAG, toString() + " STOPPED");
        running = false;
        this.changesProcessed = this.changesTotal = 0;

        saveLastSequence();
        setChanged();
        notifyObservers();

        batcher = null;
        db = null;
    }

    protected void login() {
        Map<String, String> loginParameters = getAuthorizer().loginParametersForSite(remote);
        if (loginParameters == null) {
            Log.d(CBLDatabase.TAG, String.format("%s: %s has no login parameters, so skipping login", this, getAuthorizer()));
            fetchRemoteCheckpointDoc();
            return;
        }

        final String loginPath = getAuthorizer().loginPathForSite(remote);

        Log.d(CBLDatabase.TAG, String.format("%s: Doing login with %s at %s", this, getAuthorizer().getClass(), loginPath));
        asyncTaskStarted();
        sendAsyncRequest("POST", loginPath, loginParameters, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if (e != null && e instanceof HttpResponseException && ((HttpResponseException) e).getStatusCode() != 404) {
                    Log.d(CBLDatabase.TAG, String.format("%s: Login failed for path: %s", this, loginPath));
                    error = e;
                } else {
                    Log.d(CBLDatabase.TAG, String.format("%s: Successfully logged in!", this));
                    fetchRemoteCheckpointDoc();
                }
                asyncTaskFinished(1);
            }

        });

    }

    public synchronized void asyncTaskStarted() {
        ++asyncTaskCount;
    }

    public synchronized void asyncTaskFinished(int numTasks) {
        this.asyncTaskCount -= numTasks;
        if (asyncTaskCount == 0) {
            if (!continuous) {
                stopped();
            }
        }
    }

    public void addToInbox(CBLRevision rev) {
        if (batcher.count() == 0) {
            active = true;
        }
        batcher.queueObject(rev);
        //Log.v(CBLDatabase.TAG, String.format("%s: Received #%d %s", toString(), rev.getSequence(), rev.toString()));
    }

    public void processInbox(CBLRevisionList inbox) {

    }

    public void sendAsyncRequest(String method, String relativePath, Object body, CBLRemoteRequestCompletionBlock onCompletion) {
        //Log.v(CBLDatabase.TAG, String.format("%s: %s .%s", toString(), method, relativePath));
        try {
            String urlStr = remote.toExternalForm() + relativePath;
            URL url = new URL(urlStr);
            sendAsyncRequest(method, url, body, onCompletion);
        } catch (MalformedURLException e) {
            Log.e(CBLDatabase.TAG, "Malformed URL for async request", e);
        }
    }

    public void sendAsyncRequest(String method, URL url, Object body, CBLRemoteRequestCompletionBlock onCompletion) {
        Log.d(CBLDatabase.TAG, String.format("%s: sendAsyncRequest to %s", toString(), url));
        CBLRemoteRequest request = new CBLRemoteRequest(workExecutor, clientFacotry, method, url, body, onCompletion, httpContext);
        remoteRequestExecutor.execute(request);
    }

    /**
     * CHECKPOINT STORAGE: *
     */

    public void maybeCreateRemoteDB() {
        // CBLPusher overrides this to implement the .createTarget option
    }

    /**
     * This is the _local document ID stored on the remote server to keep track of state.
     * Its ID is based on the local database ID (the private one, to make the result unguessable)
     * and the remote database's URL.
     */
    public String remoteCheckpointDocID() {
        if (db == null) {
            return null;
        }
        String input = db.privateUUID() + "\n" + remote.toExternalForm() + "\n" + (isPush() ? "1" : "0");
        return CBLMisc.TDHexSHA1Digest(input.getBytes());
    }

    public void fetchRemoteCheckpointDoc() {
        lastSequenceChanged = false;
        final String localLastSequence = db.lastSequenceWithRemoteURL(remote, isPush());
        if (localLastSequence == null) {
            maybeCreateRemoteDB();
            beginReplicating();
            return;
        }

        asyncTaskStarted();
        sendAsyncRequest("GET", "/_local/" + remoteCheckpointDocID(), null, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if (e != null && e instanceof HttpResponseException && ((HttpResponseException) e).getStatusCode() != 404) {
                    error = e;
                } else {
                    if (e instanceof HttpResponseException && ((HttpResponseException) e).getStatusCode() == 404) {
                        maybeCreateRemoteDB();
                    }
                    Map<String, Object> response = (Map<String, Object>) result;
                    remoteCheckpoint = response;
                    String remoteLastSequence = null;
                    if (response != null) {
                        remoteLastSequence = (String) response.get("lastSequence");
                    }
                    if (remoteLastSequence != null && remoteLastSequence.equals(localLastSequence)) {
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
        if (!lastSequenceChanged) {
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
        final Map<String, Object> body = new HashMap<String, Object>();
        if (remoteCheckpoint != null) {
            body.putAll(remoteCheckpoint);
        }
        body.put("lastSequence", lastSequence);

        String remoteCheckpointDocID = remoteCheckpointDocID();
        if (remoteCheckpointDocID == null) {
            return;
        }
        savingCheckpoint = true;
        sendAsyncRequest("PUT", "/_local/" + remoteCheckpointDocID, body, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                savingCheckpoint = false;
                if (e != null) {
                    Log.v(CBLDatabase.TAG, this + ": Unable to save remote checkpoint", e);
                    // TODO: If error is 401 or 403, and this is a pull, remember that remote is read-only and don't attempt to read its checkpoint next time.
                } else {
                    Map<String, Object> response = (Map<String, Object>) result;
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
