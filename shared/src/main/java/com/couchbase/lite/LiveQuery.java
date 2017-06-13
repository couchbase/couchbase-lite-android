package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 */
public class LiveQuery extends Query implements DatabaseChangeListener {

    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private final static String TAG = Log.QUERY;
    private final static long kDefaultLiveQueryUpdateInterval = 200; // 0.2sec (200ms)

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    /**
     * The shortest interval at which the query will update, regardless of how often the
     * database changes. Defaults to 0.2 sec. Increase this if the query is expensive and
     * the database updates frequently, to limit CPU consumption.
     */
    private long updateInterval;

    private Set<QueryChangeListener> queryChangeListener;

    // TODO: Using Java provided implementation, there is possibility to consume too much memory.
    //       Write own implementaiton of java.util.List, requires a lot of coding.
    //
    //private List<QueryRow> rows; // NSArray* _rows;

    ResultSet rs;                // CBLQueryEnumerator* _enum;
    private boolean observing;
    private boolean willUpdate;
    private boolean forceReload;
    private long lastUpdatedAt;


    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ LiveQuery(Query query) {
        copy(query);
        updateInterval = kDefaultLiveQueryUpdateInterval;
        queryChangeListener = Collections.synchronizedSet(new HashSet<QueryChangeListener>());
        //rows = null;
        rs = null;
        observing = false;
        willUpdate = false;
        forceReload = false;
        lastUpdatedAt = 0L;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Starts observing database changes. The .rows property will now update automatically.
     * (You usually don't need to call this yourself, since accessing or observing the getRows()
     * property will call -start for you.)
     */
    public void start() {
        if (!observing) {
            observing = true;
            getDatabase().addChangeListener(this);
            update();
//
//            new Handler(Looper.getMainLooper())
//                    .post(new Runnable() {
//                        @Override
//                        public void run() {
//                            update();
//                        }
//                    });
        }
    }

    /**
     * Stops observing database changes. Calling start() or getRows() will restart it.
     */
    public void stop() {
        if (observing) {
            observing = false;
            getDatabase().removeChangeListener(this);
        }
        willUpdate = false; // cancels the delayed update started by -databaseChanged
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(long updateInterval) {
        this.updateInterval = updateInterval;
    }

    /**
     * The current query results; this updates as the database changes.
     * Its value will be null until the initial asynchronous query finishes.
     */
    //public List<QueryRow> getRows() {
    public ResultSet getRS() {
        start();
        return rs;
    }

    /**
     * If non null, the error of the last execution of the query.
     * If null, the last execution of the query was successful.
     */
    public Throwable getLastError() {
        return null;
    }

    public void addQueryChangeListener(QueryChangeListener listener) {
        queryChangeListener.add(listener);
        if (queryChangeListener.size() == 1)
            start();
    }

    public void removeQueryChangeListener(QueryChangeListener listener) {
        queryChangeListener.remove(listener);
        if (queryChangeListener.size() == 0)
            stop();
    }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------
    @Override
    public void changed(DatabaseChange change) {
        if (willUpdate)
            return; // Already a pending update scheduled

        // Use double the update interval if this is a remote change (coming from a pull replication):
        long updateInterval = this.updateInterval;
        // TODO: isExternal() is not public API. For now, not implement
        // if(change.isExternal())
        //    updateInterval *= 2;

        // Schedule an update, respecting the updateInterval:
        long updateDelay = lastUpdatedAt + updateInterval - System.currentTimeMillis();
        updateDelay = Math.max(0, Math.min(this.updateInterval, updateDelay));
        update(updateDelay);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    /**
     * @param delay millisecond
     */
    private void update(long delay) {
        if (willUpdate)
            return; // Already a pending update scheduled

        willUpdate = true;
        // create one doc
        new Handler(Looper.getMainLooper())
                .postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        update();
                    }
                }, delay); // ms
    }

    private void update() {
        // TODO: Make this asynchronous (as in 1.x)
        try {
            Log.i(TAG, "%s: Querying...", this);
            ResultSet oldRS = rs;
            ResultSet newRS;
            if (oldRS == null || !oldRS.isValidEnumerator() || forceReload)
                newRS = run();
            else
                newRS = oldRS.refresh();

            willUpdate = forceReload = false;
            lastUpdatedAt = System.currentTimeMillis();

            if (newRS != null) {
                if (oldRS != null)
                    Log.i(TAG, "%s: Changed!", this);
                rs = newRS;
                sendNotification(new QueryChange(rs, null));
            } else {
                Log.i(TAG, "%s: ...no change", this);
            }
        } catch (CouchbaseLiteException e) {
            sendNotification(new QueryChange(null, e));
        }
    }

    private void sendNotification(QueryChange change) {
        synchronized (queryChangeListener) {
            for (QueryChangeListener listener : queryChangeListener) {
                listener.changed(change);
            }
        }
    }
}
