//
// AbstractReplicator.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.C4ReplicatorMode;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.C4WebSocketCloseCode;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-short
 * or continuous. The replicator runs asynchronously, so observe the status property to
 * be notified of progress.
 */
public abstract class AbstractReplicator extends NetworkReachabilityListener {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;
    private static final String[] kC4ReplicatorActivityLevelNames = {
        "stopped", "offline", "connecting", "idle", "busy"
    };
    private static final int kMaxOneShotRetryCount = 2;
    private static final int kMaxRetryDelay = 10 * 60; // 10min (600 sec)

    /**
     * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
     * dividing the two will produce a fraction that can be used to draw a progress bar.
     */
    public static final class Progress {
        //---------------------------------------------
        // member variables
        //---------------------------------------------

        // The number of completed changes processed.
        private final long completed;

        // The total number of changes to be processed.
        private final long total;

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

        @NonNull
        @Override
        public String toString() {
            return "Progress{" +
                "completed=" + completed +
                ", total=" + total +
                '}';
        }

        Progress copy() {
            return new Progress(completed, total);
        }
    }

    /**
     * Combined activity level and progress of a replicator.
     */
    public static final class Status {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        private final ActivityLevel activityLevel;
        private final Progress progress;
        private final CouchbaseLiteException error;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private Status(ActivityLevel activityLevel, Progress progress, CouchbaseLiteException error) {
            this.activityLevel = activityLevel;
            this.progress = progress;
            this.error = error;
        }

        public Status(C4ReplicatorStatus c4Status) {
            // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
            activityLevel = ActivityLevel.values()[c4Status.getActivityLevel()];
            progress = new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal());
            error = c4Status.getErrorCode() != 0 ? CBLStatus.convertError(c4Status.getC4Error()) : null;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The current activity level.
         */
        @NonNull
        public ActivityLevel getActivityLevel() {
            return activityLevel;
        }

        /**
         * The current progress of the replicator.
         */
        @NonNull
        public Replicator.Progress getProgress() {
            return progress;
        }

        public CouchbaseLiteException getError() {
            return error;
        }

        @NonNull
        @Override
        public String toString() {
            return "Status{" +
                "activityLevel=" + activityLevel +
                ", progress=" + progress +
                ", error=" + error +
                '}';
        }

        Status copy() {
            return new Status(activityLevel, progress.copy(), error);
        }
    }

    //---------------------------------------------
    // static initializer
    //---------------------------------------------

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

        private final int value;

        ActivityLevel(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }


    /**
     * An enum representing level of opt in on progress of replication
     * OVERALL: No additional replication progress callback
     * PER_DOCUMENT: >=1 Every document replication ended callback
     * PER_ATTACHMENT: >=2 Every blob replication progress callback
     */
    enum ReplicatorProgressLevel {
        OVERALL(0),
        PER_DOCUMENT(1),
        PER_ATTACHMENT(2);

        private final int value;

        ReplicatorProgressLevel(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    private static int retryDelay(int retryCount) {
        return Math.min(1 << Math.min(retryCount, 30), kMaxRetryDelay);
    }

    private static boolean isPush(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorConfiguration.ReplicatorType.PUSH;
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private static boolean isPull(ReplicatorConfiguration.ReplicatorType type) {
        return type == ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL
            || type == ReplicatorConfiguration.ReplicatorType.PULL;
    }

    private static int mkmode(boolean active, boolean continuous) {
        if (active && !continuous) { return C4ReplicatorMode.C4_ONE_SHOT; }
        else if (active && continuous) { return C4ReplicatorMode.C4_CONTINUOUS; }
        else { return C4ReplicatorMode.C4_DISABLED; }
    }


    private final Object lock = new Object();

    private Status status;
    private Set<ReplicatorChangeListenerToken> changeListenerTokens;
    private Set<DocumentReplicationListenerToken> docEndedListenerTokens;
    private C4Replicator c4repl;
    private C4ReplicatorStatus c4ReplStatus;
    private C4ReplicatorListener c4ReplListener;
    private C4ReplicationFilter c4ReplPushFilter;
    private C4ReplicationFilter c4ReplPullFilter;
    private ReplicatorProgressLevel progressLevel = ReplicatorProgressLevel.OVERALL;
    private int retryCount;
    private CouchbaseLiteException lastError;
    private String desc;
    private ScheduledExecutorService handler;
    private AbstractNetworkReachabilityManager reachabilityManager;

    private Map<String, Object> responseHeaders; // Do something with these (for auth)
    private boolean shouldResetCheckpoint; // Reset the replicator checkpoint.

    ReplicatorConfiguration config;

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config
     */
    public AbstractReplicator(@NonNull ReplicatorConfiguration config) {
        if (config == null) { throw new IllegalArgumentException("config cannot be null."); }

        this.config = config.readonlyCopy();
        this.changeListenerTokens = Collections.synchronizedSet(new HashSet<>());
        this.docEndedListenerTokens = Collections.synchronizedSet(new HashSet<>());
        this.handler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable target) {
                return new Thread(target, "ReplicatorListenerThread");
            }
        });
    }

    /**
     * Starts the replicator. This method returns immediately; the replicator runs asynchronously
     * and will report its progress throuh the replicator change notification.
     */
    public void start() {
        synchronized (lock) {
            Log.i(DOMAIN, "Replicator is starting .....");
            if (c4repl != null) {
                Log.i(DOMAIN, "%s has already started", this);
                return;
            }

            Log.i(DOMAIN, "%s: Starting", this);
            retryCount = 0;
            internalStart();
        }
    }

    /**
     * Stops a running replicator. This method returns immediately; when the replicator actually
     * stops, the replicator will change its status's activity level to `kCBLStopped`
     * and the replicator change notification will be notified accordingly.
     */
    public void stop() {
        synchronized (lock) {
            Log.i(DOMAIN, "%s: Replicator is stopping ...", this);
            if (c4repl != null) {
                c4repl.stop(); // this is async; status will change when repl actually stops
            }
            else { Log.i(DOMAIN, "%s: Replicator has been stopped or offlined ...", this); }

            if (c4ReplStatus.getActivityLevel() == C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_OFFLINE) {
                Log.i(DOMAIN, "%s: Replicator has been offlined; " +
                    "make the replicator into the stopped state now.", this);
                final C4ReplicatorStatus c4replStatus = new C4ReplicatorStatus();
                c4replStatus.setActivityLevel(C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_STOPPED);
                this.c4StatusChanged(c4replStatus);
            }

            if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
        }
    }

    /**
     * The replicator's configuration.
     *
     * @return
     */
    @NonNull
    public ReplicatorConfiguration getConfig() { return config.readonlyCopy(); }

    /**
     * The replicator's current status: its activity level and progress. Observable.
     *
     * @return
     */
    @NonNull
    public Status getStatus() { return status.copy(); }

    @NonNull
    public ListenerToken addChangeListener(@NonNull ReplicatorChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        return addChangeListener(null, listener);
    }

    /**
     * Set the given ReplicatorChangeListener to the this replicator.
     *
     * @param listener
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull ReplicatorChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        synchronized (lock) {
            if (listener == null) { throw new IllegalArgumentException(); }
            final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
            changeListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Remove the given ReplicatorChangeListener or DocumentReplicationListener from the this replicator.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        if (token == null) { throw new IllegalArgumentException("token cannot be null."); }

        synchronized (lock) {
            if (!((token instanceof ReplicatorChangeListenerToken)
                || (token instanceof DocumentReplicationListenerToken))) {
                throw new IllegalArgumentException();
            }
            changeListenerTokens.remove(token);
            docEndedListenerTokens.remove(token);
            if (docEndedListenerTokens.size() == 0) {
                setProgressLevel(ReplicatorProgressLevel.OVERALL);
            }
        }
    }

    /**
     * Set the given DocumentReplicationListener to the this replicator.
     *
     * @param listener
     * @return ListenerToken A token to remove the handler later
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(@NonNull DocumentReplicationListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        return addDocumentReplicationListener(null, listener);
    }

    /**
     * Set the given DocumentReplicationListener to the this replicator.
     *
     * @param listener
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(
        Executor executor,
        @NonNull DocumentReplicationListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        synchronized (lock) {
            setProgressLevel(ReplicatorProgressLevel.PER_DOCUMENT);
            final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(executor, listener);
            docEndedListenerTokens.add(token);
            return token;
        }
    }

    /**
     * Resets the local checkpoint of the replicator, meaning that it will read all
     * changes since the beginning of time from the remote database. This can only be
     * called when the replicator is in a stopped state.
     */
    public void resetCheckpoint() {
        synchronized (lock) {
            if (c4ReplStatus != null && c4ReplStatus
                .getActivityLevel() != C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_STOPPED) {
                throw new IllegalStateException(
                    "Replicator is not stopped. Resetting checkpoint is only allowed when the replicator is in the "
                        + "stopped state.");
            }
            shouldResetCheckpoint = true;
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return desc;
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Implementation of NetworkReachabilityListener
    //---------------------------------------------
    @Override
    void networkReachable() {
        synchronized (lock) {
            if (c4repl == null) {
                Log.i(DOMAIN, "%s: Server may now be reachable; retrying...", this);
                retryCount = 0;
                retry();
            }
        }
    }

    @Override
    void networkUnreachable() {
        Log.v(DOMAIN, "%s: Server may NOT be reachable now.", this);
    }

    // - (void) dealloc
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        clearRepl();
        if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
        super.finalize();
    }

    abstract void initSocketFactory(Object socketFactoryContext);

    //---------------------------------------------
    // Private member methods (in class only)
    //---------------------------------------------

    abstract int framing();

    abstract String schema();

    private void internalStart() {
        // Source
        final C4Database db = config.getDatabase().getC4Database();

        // Target:
        String schema = null;
        String host = null;
        int port = 0;
        String path = null;

        final URI remoteUri = config.getTargetURI();
        String dbName = null;
        C4Database otherDB = null;
        // replicate against remote endpoint
        if (remoteUri != null) {
            schema = remoteUri.getScheme();
            host = remoteUri.getHost();
            // NOTE: litecore use 0 for not set
            port = remoteUri.getPort() <= 0 ? 0 : remoteUri.getPort();
            path = StringUtils.stringByDeletingLastPathComponent(remoteUri.getPath());
            dbName = StringUtils.lastPathComponent(remoteUri.getPath());
        }
        // replicate against other database
        else {
            otherDB = config.getTargetDatabase() != null ? config.getTargetDatabase().getC4Database() : null;
        }

        // Encode the options:
        final Map<String, Object> options = config.effectiveOptions();

        // Update shouldResetCheckpoint flag if needed:
        if (shouldResetCheckpoint) {
            options.put(ReplicatorConfiguration.kC4ReplicatorResetCheckpoint, true);
            // Clear the reset flag, it is a one-time thing
            shouldResetCheckpoint = false;
        }

        options.put(ReplicatorConfiguration.kC4ReplicatorOptionProgressLevel, progressLevel.value);

        byte[] optionsFleece = null;
        if (options.size() > 0) {
            final FLEncoder enc = new FLEncoder();
            enc.write(options);
            try {
                optionsFleece = enc.finish();
            }
            catch (LiteCoreException e) {
                Log.e(DOMAIN, "Failed to encode", e);
            }
            finally {
                enc.free();
            }
        }

        // Figure out C4Socket Factory class based on target type:
        // Note: We should call this method something else:
        initSocketFactory(this);

        final int framing = framing();
        if (schema() != null) { schema = schema(); }

        // This allow the socket callback to map from the socket factory context
        // and the replicator:
        C4Socket.socketFactoryContext.put(this, (Replicator) this);

        // Push / Pull / Continuous:
        final boolean push = isPush(config.getReplicatorType());
        final boolean pull = isPull(config.getReplicatorType());
        final boolean continuous = config.isContinuous();

        if (config.getPushFilter() != null) {
            c4ReplPushFilter = new C4ReplicationFilter() {
                @Override
                public boolean validationFunction(
                    final String docID, final int flags, final long dict,
                    final boolean isPush, final Object context) {
                    final AbstractReplicator replicator = (AbstractReplicator) context;
                    return replicator.validationFunction(docID, documentFlags(flags), dict, isPush);
                }
            };
        }

        if (config.getPullFilter() != null) {
            c4ReplPullFilter = new C4ReplicationFilter() {
                @Override
                public boolean validationFunction(
                    final String docID, final int flags, final long dict,
                    final boolean isPush, final Object context) {
                    final AbstractReplicator replicator = (AbstractReplicator) context;
                    return replicator.validationFunction(docID, documentFlags(flags), dict, isPush);
                }
            };
        }

        c4ReplListener = new C4ReplicatorListener() {
            @Override
            public void statusChanged(final C4Replicator repl, final C4ReplicatorStatus status, final Object context) {
                Log.i(DOMAIN, "C4ReplicatorListener.statusChanged, context: " + context + ", status: " + status);
                final AbstractReplicator replicator = (AbstractReplicator) context;
                if (repl == replicator.c4repl) {
                    handler.execute(new Runnable() {
                        @Override
                        public void run() {
                            replicator.c4StatusChanged(status);
                        }
                    });
                }
            }

            @Override
            public void documentEnded(
                C4Replicator repl, final boolean pushing,
                final C4DocumentEnded[] documents,
                Object context) {
                final AbstractReplicator replicator = (AbstractReplicator) context;
                if (repl == replicator.c4repl) {
                    handler.execute(new Runnable() {
                        @Override
                        public void run() {
                            replicator.documentEnded(pushing, documents);
                        }
                    });
                }
            }
        };

        // Create a C4Replicator:
        C4ReplicatorStatus status;
        try {
            synchronized (config.getDatabase().getLock()) {
                c4repl = db.createReplicator(schema, host, port, path, dbName, otherDB,
                    mkmode(push, continuous), mkmode(pull, continuous),
                    optionsFleece,
                    c4ReplListener,
                    c4ReplPushFilter,
                    c4ReplPullFilter,
                    this,
                    this,
                    framing);
            }
            status = c4repl.getStatus();
            config.getDatabase().getActiveReplications().add((Replicator) this); // keeps me from being deallocated
        }
        catch (LiteCoreException e) {
            status = new C4ReplicatorStatus(
                C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_STOPPED,
                0,
                0,
                0,
                e.domain,
                e.code,
                0);
        }
        updateStateProperties(status);

        // Post an initial notification:
        c4ReplListener.statusChanged(c4repl, c4ReplStatus, this);
    }

    private void documentEnded(boolean pushing, C4DocumentEnded[] docEnds) {
        final List<ReplicatedDocument> docs = new ArrayList<>();
        for (C4DocumentEnded docEnd : docEnds) {
            final String docID = docEnd.getDocID();
            C4Error error = docEnd.getC4Error();
            if (!pushing
                && docEnd.getErrorDomain() == C4Constants.C4ErrorDomain.LiteCoreDomain
                && docEnd.getErrorCode() == C4Constants.LiteCoreError.kC4ErrorConflict) {
                // Conflict pulling a document -- the revision was added but app needs to resolve it:
                Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docID);
                try {
                    this.config.getDatabase().resolveConflictInDocument(docID);
                    error = new C4Error();
                }
                catch (CouchbaseLiteException ex) {
                    Log.e(DOMAIN, "Failed to resolveConflict: docID -> %s", ex, docID);
                }
            }
            final ReplicatedDocument doc
                = new ReplicatedDocument(docID, docEnd.getFlags(), error, docEnd.errorIsTransient());
            docs.add(doc);
        }
        notifyDocumentEnded(new DocumentReplication((Replicator) this, pushing, docs));
    }

    private void notifyDocumentEnded(DocumentReplication update) {
        synchronized (lock) {
            for (DocumentReplicationListenerToken token : docEndedListenerTokens) { token.notify(update); }
        }
        Log.i(DOMAIN, "C4ReplicatorListener.documentEnded() " + update.toString());
    }

    private EnumSet<DocumentFlag> documentFlags(int flags) {
        final EnumSet<DocumentFlag> documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.C4RevisionFlags.kRevDeleted) == C4Constants.C4RevisionFlags.kRevDeleted) {
            documentFlags.add(DocumentFlag.DocumentFlagsDeleted);
        }
        if ((flags & C4Constants.C4RevisionFlags.kRevPurged) == C4Constants.C4RevisionFlags.kRevPurged) {
            documentFlags.add(DocumentFlag.DocumentFlagsAccessRemoved);
        }
        return documentFlags;
    }

    private boolean validationFunction(String docID, EnumSet<DocumentFlag> flags, long dict, boolean isPush) {
        final Document document = new Document(config.getDatabase(), docID, new FLDict(dict));
        if (isPush) { return config.getPushFilter().filtered(document, flags); }
        else { return config.getPullFilter().filtered(document, flags); }
    }

    private void retry() {
        synchronized (lock) {
            if (c4repl != null || this.c4ReplStatus
                .getActivityLevel() != C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_OFFLINE) { return; }
            Log.i(DOMAIN, "%s: Retrying...", this);
            internalStart();
        }
    }

    private void c4StatusChanged(C4ReplicatorStatus c4Status) {
        synchronized (lock) {
            if (responseHeaders == null && c4repl != null) {
                final byte[] h = c4repl.getResponseHeaders();
                if (h != null) { responseHeaders = FLValue.fromData(h).asDict(); }
            }

            Log.i(DOMAIN, "%s: status changed: " + c4Status, this);
            if (c4Status.getActivityLevel() == C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_STOPPED) {
                if (handleError(c4Status.getC4Error())) {
                    // Change c4Status to offline, so my state will reflect that, and proceed:
                    c4Status.setActivityLevel(C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_OFFLINE);
                }
            }
            else if (c4Status.getActivityLevel() > C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_CONNECTING) {
                retryCount = 0;
                if (reachabilityManager != null) { reachabilityManager.removeNetworkReachabilityListener(this); }
            }

            // Update my properties:
            updateStateProperties(c4Status);

            // Post notification
            synchronized (lock) {
                // Replicator.getStatus() creates a copy of Status.
                final ReplicatorChange change = new ReplicatorChange((Replicator) this, this.getStatus());
                for (ReplicatorChangeListenerToken token : changeListenerTokens) { token.notify(change); }
            }

            // If Stopped:
            if (c4Status.getActivityLevel() == C4ReplicatorStatus.C4ReplicatorActivityLevel.C4_STOPPED) {
                // Stopped
                this.clearRepl();
                config.getDatabase().getActiveReplications().remove(this); // this is likely to dealloc me
            }
        }
    }

    // - (bool) handleError: (C4Error)c4err
    private boolean handleError(C4Error c4err) {
        // If this is a transient error, or if I'm continuous and the error might go away with a change
        // in network (i.e. network down, hostname unknown), then go offline and retry later.
        final boolean isTransient = C4Replicator.mayBeTransient(c4err) ||
            ((c4err.getDomain() == C4Constants.C4ErrorDomain.WebSocketDomain) &&
                (c4err.getCode() == C4WebSocketCloseCode.kWebSocketCloseUserTransient));

        final boolean isNetworkDependent = C4Replicator.mayBeNetworkDependent(c4err);
        if (!isTransient && !(config.isContinuous() && isNetworkDependent)) {
            return false; // nope, this is permanent
        }
        if (!config.isContinuous() && retryCount >= kMaxOneShotRetryCount) {
            return false; //too many retries
        }

        clearRepl();

        if (!isTransient) {
            Log.i(DOMAIN, "%s: Network error (%s); will retry when network changes...", this, c4err);
        }
        else {
            // On transient error, retry periodically, with exponential backoff:
            final int delay = retryDelay(++retryCount);
            Log.i(DOMAIN, "%s: Transient error (%s); will retry in %d sec...", this, c4err, delay);
            handler.schedule(
                new Runnable() {
                    @Override
                    public void run() {
                        retry();
                    }
                },
                delay,
                TimeUnit.SECONDS);
        }

        // Also retry when the network changes:
        startReachabilityObserver();
        return true;
    }

    private void updateStateProperties(C4ReplicatorStatus status) {
        CouchbaseLiteException error = null;
        if (status.getErrorCode() != 0) {
            error = CBLStatus.convertException(
                status.getErrorDomain(),
                status.getErrorCode(),
                status.getErrorInternalInfo());
        }
        if (error != this.lastError) { this.lastError = error; }

        c4ReplStatus = status.copy();

        // Note: c4Status.level is current matched with CBLReplicatorActivityLevel:
        final ActivityLevel level = ActivityLevel.values()[status.getActivityLevel()];

        this.status = new Status(
            level,
            new Progress((int) status.getProgressUnitsCompleted(), (int) status.getProgressUnitsTotal()),
            error);

        Log.i(DOMAIN, "%s is %s, progress %d/%d, error: %s",
            this,
            kC4ReplicatorActivityLevelNames[status.getActivityLevel()],
            status.getProgressUnitsCompleted(),
            status.getProgressUnitsTotal(),
            error);
    }

    private void startReachabilityObserver() {
        final URI remoteUri = config.getTargetURI();

        // target is databaes
        if (remoteUri == null) { return; }

        try {
            final InetAddress host = InetAddress.getByName(remoteUri.getHost());
            if (host.isAnyLocalAddress() || host.isLoopbackAddress()) { return; }
        }
        // an unknown host is surely not local
        catch (UnknownHostException ignore) { }

        if (reachabilityManager == null) {
            reachabilityManager = new NetworkReachabilityManager();
        }

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
        return String.format(Locale.ENGLISH, "%s[%s%s%s %s %s]",
            Replicator.class.getSimpleName(),
            isPull(config.getReplicatorType()) ? "<" : "",
            config.isContinuous() ? "*" : "-",
            isPush(config.getReplicatorType()) ? ">" : "",
            config.getDatabase(),
            config.getTarget());
    }

    //---------------------------------------------
    // Private static methods (in class only)
    //---------------------------------------------

    private void setProgressLevel(ReplicatorProgressLevel level) {
        progressLevel = level;
    }

    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    static {
        //Register CBLWebSocket which is C4Socket implementation
        //CBLWebSocket.register();
    }
}
