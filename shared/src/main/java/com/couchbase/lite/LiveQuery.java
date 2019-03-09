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

import android.support.annotation.NonNull;

import java.util.Locale;
import java.util.concurrent.Executor;

import com.couchbase.lite.internal.support.Log;


/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 */
final class LiveQuery implements DatabaseChangeListener {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;
    private static final long kDefaultLiveQueryUpdateInterval = 200; // 0.2sec (200ms)

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final AbstractQuery query;
    private final Object lock = new Object(); // lock for thread-safety
    private ChangeNotifier<QueryChange> changeNotifier;
    private ResultSet resultSet;
    private boolean observing;
    private boolean willUpdate;
    private long lastUpdatedAt;
    private ListenerToken dbListenerToken;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    LiveQuery(AbstractQuery query) {
        if (query == null) { throw new IllegalArgumentException("query should not be null."); }

        this.query = query;
        this.changeNotifier = new ChangeNotifier<>();
        this.resultSet = null;
        this.observing = false;
        this.willUpdate = false;
        this.lastUpdatedAt = 0L;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[%s]", this.getClass().getSimpleName(), query.toString());
    }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------

    @Override
    public void changed(@NonNull DatabaseChange change) {
        synchronized (lock) {
            if (willUpdate) {
                return; // Already a pending update scheduled
            }

            if (!observing) { return; }

            // Schedule an update, respecting the updateInterval:
            long updateDelay = lastUpdatedAt + kDefaultLiveQueryUpdateInterval - System.currentTimeMillis();
            updateDelay = Math.max(0, Math.min(kDefaultLiveQueryUpdateInterval, updateDelay));
            update(updateDelay);
        }
    }

    //---------------------------------------------
    // protected methods
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        stop(true);
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
            if (query.getDatabase() == null) {
                throw new IllegalArgumentException("associated database should not be null.");
            }

            observing = true;
            releaseResultSet();
            query.getDatabase().getActiveLiveQueries().add(this);
            // NOTE: start() method could be called during LiveQuery is running.
            // Ex) Query.setParameters() with LiveQuery.
            if (dbListenerToken == null) { dbListenerToken = query.getDatabase().addChangeListener(this); }
            update(0);
        }
    }

    /**
     * Adds a change listener.
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    ListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        synchronized (lock) {
            if (!observing) { start(); }
            return changeNotifier.addChangeListener(executor, listener);
        }
    }

    /**
     * Removes a change listener
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    void removeChangeListener(ListenerToken token) {
        synchronized (lock) {
            if (changeNotifier.removeChangeListener(token) == 0) { stop(true); }
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    /**
     * Stops observing database changes.
     */
    private void stop(boolean removeFromList) {
        synchronized (lock) {
            observing = false;
            willUpdate = false; // cancels the delayed update started by -databaseChanged
            if (query != null && query.getDatabase() != null && dbListenerToken != null) {
                query.getDatabase().removeChangeListener(dbListenerToken);
                dbListenerToken = null;
            }
            if (removeFromList && query != null && query.getDatabase() != null) {
                query.getDatabase().getActiveLiveQueries().remove(this);
            }
            releaseResultSet();
        }
    }

    /**
     * NOTE: update(long delay) is only called from synchronzied LiveQuery methods by lock.
     *
     * @param delay millisecond
     */
    private void update(long delay) {
        if (willUpdate) {
            return; // Already a pending update scheduled
        }

        if (!observing) { return; }

        willUpdate = true;

        query.getDatabase().scheduleOnQueryExecutor(new Runnable() {
            @Override
            public void run() {
                update();
            }
        }, delay);
    }

    /**
     * NOTE: update() method is called from only ExecutorService for LiveQuery which is
     * a single thread. But update changes and refers some instant variables
     */
    private void update() {
        synchronized (lock) {
            if (!observing) { return; }

            try {
                Log.i(DOMAIN, "%s: Querying...", this);
                final ResultSet oldResultSet = resultSet;
                final ResultSet newResultSet = (oldResultSet == null) ? query.execute() : oldResultSet.refresh();

                willUpdate = false;
                lastUpdatedAt = System.currentTimeMillis();

                if (newResultSet != null) {
                    if (oldResultSet != null) { Log.i(DOMAIN, "%s: Changed!", this); }
                    resultSet = newResultSet;
                    changeNotifier.postChange(new QueryChange(this.query, resultSet, null));
                }
                else {
                    Log.i(DOMAIN, "%s: ...no change", this);
                }
            }
            catch (CouchbaseLiteException e) {
                changeNotifier.postChange(new QueryChange(this.query, null, e));
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
