package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;

import com.couchbase.lite.internal.support.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 */
final class LiveQuery implements DatabaseChangeListener {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private final static String TAG = Log.QUERY;
    private final static long kDefaultLiveQueryUpdateInterval = 200; // 0.2sec (200ms)

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private Set<QueryChangeListenerToken> queryChangeListenerTokens;
    private final Query query;
    private ResultSet resultSet;
    private boolean observing;
    private boolean willUpdate;
    private long lastUpdatedAt;
    private ListenerToken dbListenerToken;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    LiveQuery(Query query) {
        if (query == null)
            throw new IllegalArgumentException("query should not be null.");

        this.query = query;
        this.queryChangeListenerTokens = Collections.synchronizedSet(new HashSet<QueryChangeListenerToken>());
        this.resultSet = null;
        this.observing = false;
        this.willUpdate = false;
        this.lastUpdatedAt = 0L;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[%s]", this.getClass().getSimpleName(), query.toString());
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
        try {
            stop(true);
        } catch (CouchbaseLiteRuntimeException e) {
            Log.w(TAG, "Error in LiveQuery.finalize()", e);
        }
        super.finalize();
    }

    //---------------------------------------------
    // package
    //---------------------------------------------

    /**
     * Starts observing database changes and reports changes in the query result.
     */
    void start() {
        if (query.getDatabase() == null)
            throw new IllegalArgumentException("associated database should not be null.");

        observing = true;
        releaseResultSet();
        query.getDatabase().getActiveLiveQueries().add(this);
        dbListenerToken = query.getDatabase().addChangeListener(this);
        update();
    }

    /**
     * Stops observing database changes.
     */
    void stop(boolean removeFromList) {
        observing = false;
        willUpdate = false; // cancels the delayed update started by -databaseChanged
        query.getDatabase().removeChangeListener(dbListenerToken);
        if(removeFromList)
            query.getDatabase().getActiveLiveQueries().remove(this);
        releaseResultSet();
    }

    QueryChangeListenerToken addChangeListener(QueryChangeListener listener) {
        return addChangeListener(null, listener);
    }

    /**
     * Adds a change listener.
     */
    QueryChangeListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        QueryChangeListenerToken token = new QueryChangeListenerToken(executor, listener);
        queryChangeListenerTokens.add(token);
        if (!observing)
            start();
        return token;
    }

    /**
     * Removes a change listener
     */
    void removeChangeListener(QueryChangeListenerToken token) {
        queryChangeListenerTokens.remove(token);
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
                newResultSet = query.execute();
            else
                newResultSet = oldResultSet.refresh();

            willUpdate = false;
            lastUpdatedAt = System.currentTimeMillis();

            if (newResultSet != null) {
                if (oldResultSet != null)
                    Log.i(TAG, "%s: Changed!", this);
                resultSet = newResultSet;
                sendNotification(new QueryChange(this.query, resultSet, null));
            } else {
                Log.i(TAG, "%s: ...no change", this);
            }
        } catch (CouchbaseLiteException e) {
            sendNotification(new QueryChange(this.query, null, e));
        }
    }

    private void sendNotification(QueryChange change) {
        synchronized (queryChangeListenerTokens) {
            for (QueryChangeListenerToken token : queryChangeListenerTokens) {
                token.notify(change);
            }
        }
    }

    private void releaseResultSet() {
        if (resultSet != null) {
            resultSet.free();
            resultSet = null;
        }
    }
}
