//
// LiveQuery.java
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

import com.couchbase.lite.internal.support.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final AbstractQuery query;
    private ResultSet resultSet;
    private boolean observing;
    private boolean willUpdate;
    private long lastUpdatedAt;
    private ListenerToken dbListenerToken;
    private final Object lock = new Object(); // lock for thread-safety

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    LiveQuery(AbstractQuery query) {
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
        synchronized (lock) {
            if (willUpdate)
                return; // Already a pending update scheduled

            if (!observing)
                return;

            // Schedule an update, respecting the updateInterval:
            long updateDelay = lastUpdatedAt + kDefaultLiveQueryUpdateInterval - System.currentTimeMillis();
            updateDelay = Math.max(0, Math.min(this.kDefaultLiveQueryUpdateInterval, updateDelay));
            update(updateDelay);
        }
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
        synchronized (lock) {
            if (query.getDatabase() == null)
                throw new IllegalArgumentException("associated database should not be null.");

            observing = true;
            releaseResultSet();
            query.getDatabase().getActiveLiveQueries().add(this);
            dbListenerToken = query.getDatabase().addChangeListener(this);
            update(0);
        }
    }

    /**
     * Stops observing database changes.
     */
    void stop(boolean removeFromList) {
        synchronized (lock) {
            observing = false;
            willUpdate = false; // cancels the delayed update started by -databaseChanged
            if (query != null && query.getDatabase() != null)
                query.getDatabase().removeChangeListener(dbListenerToken);
            if (removeFromList && query != null && query.getDatabase() != null)
                query.getDatabase().getActiveLiveQueries().remove(this);
            releaseResultSet();
        }
    }

    /**
     * Adds a change listener.
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    QueryChangeListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        synchronized (lock) {
            QueryChangeListenerToken token = new QueryChangeListenerToken(executor, listener);
            queryChangeListenerTokens.add(token);
            if (!observing)
                start();
            return token;
        }
    }

    /**
     * Removes a change listener
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    void removeChangeListener(QueryChangeListenerToken token) {
        synchronized (lock) {
            queryChangeListenerTokens.remove(token);
            if (queryChangeListenerTokens.isEmpty())
                stop(true);
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    /**
     * NOTE: update(long delay) is only called from synchronzied LiveQuery methods by lock.
     *
     * @param delay millisecond
     */
    private void update(long delay) {
        if (willUpdate)
            return; // Already a pending update scheduled

        if (!observing)
            return;

        willUpdate = true;

        ScheduledExecutorService executor = query.getDatabase().getQueryExecutor();
        if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    update();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * NOTE: update() method is called from only ExecutorService for LiveQuery which is
     * a single thread. But update changes and refers some instant variables
     */
    private void update() {
        synchronized (lock) {
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
