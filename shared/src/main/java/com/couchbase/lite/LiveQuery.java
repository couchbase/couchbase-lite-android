package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 */
public class LiveQuery implements DatabaseChangeListener {

    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private final static String TAG = Log.QUERY;
    private final static long kDefaultLiveQueryUpdateInterval = 200; // 0.2sec (200ms)

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private Set<LiveQueryChangeListener> queryChangeListener;
    private Query query;
    private ResultSet resultSet;
    private boolean observing;
    private boolean willUpdate;
    private long lastUpdatedAt;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ LiveQuery(Query query) {
        if (query == null)
            throw new IllegalArgumentException("query should not be null.");

        this.query = query;
        this.queryChangeListener = Collections.synchronizedSet(new HashSet<LiveQueryChangeListener>());
        this.resultSet = null;
        this.observing = false;
        this.willUpdate = false;
        this.lastUpdatedAt = 0L;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Starts observing database changes and reports changes in the query result.
     */
    public void run() {
        observing = true;
        releaseResultSet();
        query.getDatabase().addChangeListener(this);
        update();
    }

    /**
     * Stops observing database changes.
     */
    public void stop() {
        observing = false;
        willUpdate = false; // cancels the delayed update started by -databaseChanged
        query.getDatabase().removeChangeListener(this);
        releaseResultSet();
    }

    /**
     * Adds a change listener.
     */
    public void addChangeListener(LiveQueryChangeListener listener) {
        queryChangeListener.add(listener);
    }

    /**
     * Removes a change listener
     */
    public void removeChangeListener(LiveQueryChangeListener listener) {
        queryChangeListener.remove(listener);
    }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------
    @Override
    public void changed(DatabaseChange change) {
        if (willUpdate)
            return; // Already a pending update scheduled

        if (!observing)
            return;

        // Schedule an update, respecting the updateInterval:
        long updateDelay = lastUpdatedAt + kDefaultLiveQueryUpdateInterval - System.currentTimeMillis();
        updateDelay = Math.max(0, Math.min(this.kDefaultLiveQueryUpdateInterval, updateDelay));
        update(updateDelay);
    }
    //---------------------------------------------
    // protected methods
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    /**
     * @param delay millisecond
     */
    private void update(long delay) {
        if (willUpdate)
            return; // Already a pending update scheduled

        if (!observing)
            return;

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

        if (!observing)
            return;

        try {
            Log.i(TAG, "%s: Querying...", this);
            ResultSet oldResultSet = resultSet;
            ResultSet newResultSet;
            if (oldResultSet == null)
                newResultSet = query.run();
            else
                newResultSet = oldResultSet.refresh();

            willUpdate = false;
            lastUpdatedAt = System.currentTimeMillis();

            if (newResultSet != null) {
                if (oldResultSet != null)
                    Log.i(TAG, "%s: Changed!", this);
                resultSet = newResultSet;
                sendNotification(new LiveQueryChange(this, resultSet, null));
            } else {
                Log.i(TAG, "%s: ...no change", this);
            }
        } catch (CouchbaseLiteException e) {
            sendNotification(new LiveQueryChange(this, null, e));
        }
    }

    private void sendNotification(LiveQueryChange change) {
        synchronized (queryChangeListener) {
            for (LiveQueryChangeListener listener : queryChangeListener) {
                listener.changed(change);
            }
        }
    }

    private void releaseResultSet() {
        if (resultSet != null) {
            resultSet.release();
            resultSet = null;
        }
    }
}
