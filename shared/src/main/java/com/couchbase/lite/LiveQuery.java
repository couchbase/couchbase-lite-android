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
    private boolean forceReload;
    private long lastUpdatedAt;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ LiveQuery(Query query) {
        this.query = query;
        queryChangeListener = Collections.synchronizedSet(new HashSet<LiveQueryChangeListener>());
        resultSet = null;
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
    public void run() {
        if (!observing) {
            observing = true;
            query.getDatabase().addChangeListener(this);
            update();
        }
    }

    /**
     * Stops observing database changes. Calling start() or getRows() will restart it.
     */
    public void stop() {
        if (observing) {
            observing = false;
            query.getDatabase().removeChangeListener(this);
        }
        willUpdate = false; // cancels the delayed update started by -databaseChanged
    }

    public void addChangeListener(LiveQueryChangeListener listener) {
        queryChangeListener.add(listener);
        if (queryChangeListener.size() == 1)
            run();
    }

    public void removeChangeListener(LiveQueryChangeListener listener) {
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

        // Schedule an update, respecting the updateInterval:
        long updateDelay = lastUpdatedAt + kDefaultLiveQueryUpdateInterval - System.currentTimeMillis();
        updateDelay = Math.max(0, Math.min(this.kDefaultLiveQueryUpdateInterval, updateDelay));
        update(updateDelay);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /**
     * The current query results; this updates as the database changes.
     * Its value will be null until the initial asynchronous query finishes.
     * <p>
     * NOTE: for unit test
     */
    /*package*/ ResultSet getResultSet() {
        run();
        return resultSet;
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
            ResultSet oldResultSet = resultSet;
            ResultSet newResultSet;
            if (oldResultSet == null || !oldResultSet.isValidEnumerator() || forceReload)
                newResultSet = query.run();
            else
                newResultSet = oldResultSet.refresh();

            willUpdate = forceReload = false;
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
            sendNotification(new LiveQueryChange(null, e));
        }
    }

    private void sendNotification(LiveQueryChange change) {
        synchronized (queryChangeListener) {
            for (LiveQueryChangeListener listener : queryChangeListener) {
                listener.changed(change);
            }
        }
    }
}
