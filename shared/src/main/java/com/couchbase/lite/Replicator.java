package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.couchbase.lite.internal.replicator.CBLWebSocket;
import com.couchbase.lite.internal.support.StringUtils;
import com.couchbase.litecore.C4Database;
import com.couchbase.litecore.C4Error;
import com.couchbase.litecore.C4Replicator;
import com.couchbase.litecore.C4ReplicatorListener;
import com.couchbase.litecore.C4ReplicatorStatus;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorConflict;
import static com.couchbase.litecore.C4ReplicatorMode.kC4Continuous;
import static com.couchbase.litecore.C4ReplicatorMode.kC4Disabled;
import static com.couchbase.litecore.C4ReplicatorMode.kC4OneShot;
import static com.couchbase.litecore.C4ReplicatorStatus.C4ReplicatorActivityLevel.kC4Connecting;
import static com.couchbase.litecore.C4ReplicatorStatus.C4ReplicatorActivityLevel.kC4Offline;
import static com.couchbase.litecore.C4ReplicatorStatus.C4ReplicatorActivityLevel.kC4Stopped;
import static java.util.Collections.synchronizedSet;

public class Replicator implements NetworkReachabilityListener {
    private static final String TAG = Log.SYNC;

    /**
     * Activity level of a replicator.
     */
    public enum ActivityLevel {
        /**
         * The replication is finished or hit a fatal error.
         */
        STOPPED(0),
        /**
         * The replicator is offline as the remote host is unreachable.
         */
        OFFLINE(1),
        /**
         * The replicator is connecting to the remote host.
         */
        CONNECTING(2),
        /**
         * The replication is inactive; either waiting for changes or offline as the remote host is
         * unreachable.
         */
        IDLE(3),
        /**
         * The replication is actively transferring data.
         */
        BUSY(4);

        private int value;

        ActivityLevel(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    /**
     * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
     * dividing the two will produce a fraction that can be used to draw a progress bar.
     */
    public class Progress {
        //---------------------------------------------
        // member variables
        //---------------------------------------------

        // The number of completed changes processed.
        private long completed;

        // The total number of changes to be processed.
        private long total;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        private Progress(long completed, long total) {
            this.completed = completed;
            this.total = total;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The number of completed changes processed.
         */
        public long getCompleted() {
            return completed;
        }

        /**
         * The total number of changes to be processed.
         */
        public long getTotal() {
            return total;
        }

        Progress copy() {
            return new Progress(completed, total);
        }
    }

    /**
     * Combined activity level and progress of a replicator.
     */
    public class Status {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private ActivityLevel activityLevel;
        private Progress progress;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private Status(ActivityLevel activityLevel, Progress progress) {
            this.activityLevel = activityLevel;
            this.progress = progress;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The current activity level.
         */
        public ActivityLevel getActivityLevel() {
            return activityLevel;
        }

        /**
         * The current progress of the replicator.
         */
        public Replicator.Progress getProgress() {
            return progress;
        }

        Status copy() {
            return new Status(activityLevel, progress.copy());
        }
    }

    //---------------------------------------------
    // static initializer
    //---------------------------------------------

    static {
        //Register CBLWebSocket which is C4Socket implementation
        CBLWebSocket.register();
    }


    final static String[] kC4ReplicatorActivityLevelNames = {
            "stopped", "offline", "connecting", "idle", "busy"
    };

    final static int kMaxOneShotRetryCount = 2;
    final static int kMaxRetryDelay = 10 * 60; // 10min (600 sec)

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private ReplicatorConfiguration config;
    private Status status;

    private Set<ReplicatorChangeListener> changeListeners;
    C4Replicator c4repl;
    C4ReplicatorStatus c4ReplStatus;
    C4ReplicatorListener c4ReplListener;
    int retryCount;
    CouchbaseLiteException lastError;
    String desc = null;
    Handler handler; // _dispatchQueue
    NetworkReachabilityManager reachabilityManager = null;
    Map<String, Object> responseHeaders = null; // Do something with these (for auth)

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config
     */
    public Replicator(ReplicatorConfiguration config) {
        this.config = config.copy();
        this.changeListeners = synchronizedSet(new HashSet<ReplicatorChangeListener>());
        this.handler = new Handler(Looper.getMainLooper());
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Starts the replicator. This method returns immediately; the replicator runs asynchronously
     * and will report its progress throuh the replicator change notification.
     */
    public void start() {
        if (c4repl != null) {
            Log.w(TAG, "%s has already started", this);
            return;
        }

        Log.i(TAG, "%s: Starting", this);
        retryCount = 0;
        _start();
    }


    /**
     * Stops a running replicator. This method returns immediately; when the replicator actually
     * stops, the replicator will change its status's activity level to `kCBLStopped`
     * and the replicator change notification will be notified accordingly.
     */
    public void stop() {
        if (c4repl != null)
            c4repl.stop(); // this is async; status will change when repl actually stops
        if (reachabilityManager != null)
            reachabilityManager.removeNetworkReachabilityListener(this);
    }

    /**
     * The replicator's configuration.
     *
     * @return
     */
    public ReplicatorConfiguration getConfig() {
        return config.copy();
    }

    /**
     * The replicator's current status: its activity level and progress. Observable.
     *
     * @return
     */
    public Status getStatus() {
        return status.copy();
    }

    /**
     * Set the given ReplicatorChangeListener to the this replicator.
     *
     * @param listener
     */
    public void addChangeListener(ReplicatorChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        changeListeners.add(listener);
    }

    /**
     * Remove the given ReplicatorChangeListener from the this replicator.
     *
     * @param listener
     */
    public void removeChangeListener(ReplicatorChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        changeListeners.remove(listener);
    }

    @Override
    public String toString() {
        if (desc == null)
            desc = description();
        return desc;
    }

    //---------------------------------------------
    // Implementation of NetworkReachabilityListener
    //---------------------------------------------
    @Override
    public void networkReachable() {
        if (c4repl == null) {
            Log.e(TAG, "%s: Server may now be reachable; retrying...", this);
            retryCount = 0;
            retry();
        }
    }

    @Override
    public void networkUnreachable() {
        Log.v(TAG, "%s: Server may NOT be reachable now.", this);
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    // - (void) dealloc
    @Override
    protected void finalize() throws Throwable {
        clearRepl();
        if (reachabilityManager != null) {
            reachabilityManager.removeNetworkReachabilityListener(this);
            reachabilityManager = null;
        }
        super.finalize();
    }

    //---------------------------------------------
    // Private member methods (in class only)
    //---------------------------------------------

    private void _start() {
        // Source
        C4Database db = config.getDatabase().getC4Database();

        // Target:
        String schema = null;
        String host = null;
        int port = 0;
        String path = null;


        URI remoteURI = config.getTargetURI();
        String dbName = null;
        C4Database otherDB = null;
        // replicate against remote endpoint
        if (remoteURI != null) {
            schema = remoteURI.getScheme();
            host = remoteURI.getHost();
            port = remoteURI.getPort();
            path = StringUtils.stringByDeletingLastPathComponent(remoteURI.getPath());
            dbName = StringUtils.lastPathComponent(remoteURI.getPath());
        }
        // replicate against other database
        else {
            otherDB = config.getTargetDatabase().getC4Database();
        }

        // Encode the options:
        Map<String, Object> options = config.effectiveOptions();
        byte[] optionsFleece = null;
        if (options.size() > 0) {
            FLEncoder enc = new FLEncoder();
            enc.write(options);
            try {
                optionsFleece = enc.finish();
            } catch (LiteCoreException e) {
                Log.e(TAG, "Failed to encode", e);
            }
            enc.free();
        }

        // Push / Pull / Continuous:
        boolean push = isPush(config.getReplicatorType());
        boolean pull = isPull(config.getReplicatorType());
        boolean continuous = config.isContinuous();

        c4ReplListener = new C4ReplicatorListener() {
            @Override
            public void statusChanged(final C4Replicator repl, final C4ReplicatorStatus status, final Object context) {
                Log.i(TAG, "C4ReplicatorListener.statusChanged() status -> " + status);
                final Replicator replicator = (Replicator) context;
                if (repl == replicator.c4repl) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            replicator.c4StatusChanged(status);
                        }
                    });
                }
            }

            @Override
            public void documentError(C4Replicator repl, final boolean pushing, final String docID, final C4Error error, final boolean trans, Object context) {
                final Replicator replicator = (Replicator) context;
                if (repl == replicator.c4repl) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            replicator.documentError(pushing, docID, error, trans);
                        }
                    });
                }
            }
        };

        // Create a C4Replicator:
        C4ReplicatorStatus status = null;
        try {
            c4repl = db.createReplicator(schema, host, port, path, dbName, otherDB,
                    mkmode(push, continuous), mkmode(pull, continuous),
                    optionsFleece,
                    c4ReplListener, this);
            status = c4repl.getStatus();
            config.getDatabase().getActiveReplications().add(this); // keeps me from being deallocated
        } catch (LiteCoreException e) {
            status = new C4ReplicatorStatus(kC4Stopped, 0, 0, e.domain, e.code, 0);
        }
        updateStateProperties(status);

        // Post an initial notification:
        c4ReplListener.statusChanged(c4repl, c4ReplStatus, this);
    }

    private void documentError(boolean pushing, String docID, C4Error error, boolean trans) {
        if (!pushing && error.getDomain() == LiteCoreDomain && error.getCode() == kC4ErrorConflict) {
            // Conflict pulling a document -- the revision was added but app needs to resolve it:
            Log.i(TAG, "%s: pulled conflicting version of '%s'", this, docID);
            try {
                this.config.getDatabase().resolveConflictInDocument(docID,
                        config.getConflictResolver());
            } catch (CouchbaseLiteException ex) {
                Log.e(TAG, "Failed to resolveConflict: docID -> %s", ex, docID);
                // TODO: Should pass error along to listener
            }
        } else {
            Log.i(TAG, "C4ReplicatorListener.documentError() pushing -> %s, docID -> %s, error -> %s, trans -> %s", pushing ? "true" : false, docID, error, trans ? "true" : false);
            // TODO: Should pass error along to listener
        }
    }

    /**
     * Generates authentication requests headers from username & password for basic auth
     *
     * @param username
     * @param password
     * @return
     */
    private Map<String, Object> getAuthenticatedHeaders(String username, String password) {
        byte[] cipher = String.format("%s:%s", username, password).getBytes();
        String encodedVal = Base64.encodeToString(cipher, Base64.NO_WRAP); // NOTE: Base64.DEFAULT adds new line which causes the problem
        String authHeaderValue = String.format(Locale.ENGLISH, "Basic %s", encodedVal);
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", authHeaderValue);
        return headers;
    }

    private void retry() {
        if (c4repl != null || this.c4ReplStatus.getActivityLevel() != kC4Offline)
            return;
        Log.e(TAG, "%s: Retrying...", this);
        _start();
    }

    private void c4StatusChanged(C4ReplicatorStatus c4Status) {
        if (responseHeaders == null && c4repl != null) {
            byte[] h = c4repl.getResponseHeaders();
            if (h != null)
                responseHeaders = FLValue.fromData(h).asDict();
        }

        Log.e(TAG, "statusChanged() c4Status -> " + c4Status);
        if (c4Status.getActivityLevel() == kC4Stopped) {
            if (handleError(c4Status.getC4Error())) {
                // Change c4Status to offline, so my state will reflect that, and proceed:
                c4Status.setActivityLevel(kC4Offline);
            }
        } else if (c4Status.getActivityLevel() > kC4Connecting) {
            retryCount = 0;
            if (reachabilityManager != null)
                reachabilityManager.removeNetworkReachabilityListener(this);
        }


        // Update my properties:
        updateStateProperties(c4Status);

        // Post notification
        synchronized (changeListeners) {
            for (ReplicatorChangeListener listener : changeListeners) {
                listener.changed(this, this.status, this.lastError);
            }
        }

        // If Stopped:
        if (c4Status.getActivityLevel() == kC4Stopped) {
            // Stopped
            this.clearRepl();
            config.getDatabase().getActiveReplications().remove(this); // this is likely to dealloc me
        }
    }

    // - (bool) handleError: (C4Error)c4err
    private boolean handleError(C4Error c4err) {
        // If this is a transient error, or if I'm continuous and the error might go away with a change
        // in network (i.e. network down, hostname unknown), then go offline and retry later.
        boolean bTransient = C4Replicator.mayBeTransient(c4err);
        boolean bNetworkDependent = C4Replicator.mayBeNetworkDependent(c4err);
        if (!bTransient && !(config.isContinuous() && bNetworkDependent))
            return false; // nope, this is permanent
        if (!config.isContinuous() && retryCount >= kMaxOneShotRetryCount)
            return false; //too many retries

        clearRepl();

        // TODO: convert error????

        if (bTransient) {
            // On transient error, retry periodically, with exponential backoff:
            int delay = retryDelay(++retryCount);
            Log.i(TAG, "%s: Transient error (%s); will retry in %d sec...", this, c4err, delay);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    retry();
                }
            }, delay * 1000);
        } else {
            Log.i(TAG, "%s: Network error (%s); will retry when network changes...", this, c4err);
        }

        // Also retry when the network changes:
        startReachabilityObserver();
        return true;
    }

    private static int retryDelay(int retryCount) {
        int delay = 1 << Math.min(retryCount, 30);
        return Math.min(delay, kMaxRetryDelay);
    }

    private void updateStateProperties(C4ReplicatorStatus status) {
        CouchbaseLiteException error = null;
        if (status.getErrorCode() != 0)
            error = new CouchbaseLiteException(status.getErrorDomain(), status.getErrorCode());
        if (error != this.lastError)
            this.lastError = error;

        //c4ReplStatus = status.copy();
        c4ReplStatus = status.copy();

        // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
        ActivityLevel level = ActivityLevel.values()[status.getActivityLevel()];

        Progress progress = new Progress((int) status.getProgressCompleted(), (int) status.getProgressTotal());
        this.status = new Status(level, progress);

        Log.e(TAG, "%s is %s, progress %d/%d, error: %s",
                this,
                kC4ReplicatorActivityLevelNames[status.getActivityLevel()],
                status.getProgressCompleted(),
                status.getProgressTotal(),
                error);
    }

    private void startReachabilityObserver() {
        URI remoteURI = config.getTargetURI();
        if (remoteURI == null)
            return;
        String hostname = remoteURI.getHost();
        if ("localhost".equals(hostname) || "127.0.0.1".equals(hostname))
            return;

        if (reachabilityManager == null)
            reachabilityManager = new AndroidNetworkReachabilityManager(config.getDatabase().getConfig().getContext());
        reachabilityManager.addNetworkReachabilityListener(this);
    }

    // - (void) clearRepl
    private void clearRepl() {
        if (c4repl != null) {
            c4repl.free();
            c4repl = null;
        }
    }

    // - (NSString*) description
    private String description() {
        return String.format(Locale.ENGLISH, "%s[%s%s%s %s]",
                Replicator.class.getSimpleName(),
                isPull(config.getReplicatorType()) ? "<" : "",
                config.isContinuous() ? "*" : "-",
                isPush(config.getReplicatorType()) ? ">" : "",
                config.getTarget());
    }

    //---------------------------------------------
    // Private static methods (in class only)
    //---------------------------------------------

    private static boolean isPush(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL || type == ReplicatorConfiguration.ReplicatorType.PUSH;
    }

    private static boolean isPull(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL || type == ReplicatorConfiguration.ReplicatorType.PULL;
    }

    private static int mkmode(boolean active, boolean continuous) {
        if (active && !continuous) return kC4OneShot;
        else if (active && continuous) return kC4Continuous;
        else return kC4Disabled;
    }
}
