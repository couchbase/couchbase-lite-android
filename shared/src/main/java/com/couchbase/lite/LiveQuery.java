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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.support.Log;


/**
 * A Query subclass that automatically refreshes the result rows every time the database changes.
 * <p>
 * Be careful with the state machine here:
 * A query that has been STOPPED can be STARTED again!
 * In particular, a query that is stopping when it receives a request to restart
 * should suspend the restart request, finish stopping, and then restart.
 */
final class LiveQuery implements DatabaseChangeListener {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;
    private static final long LIVE_QUERY_UPDATE_INTERVAL_MS = 200; // 0.2sec (200ms)

    private enum State {STOPPED, STARTED, SCHEDULED}

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final ChangeNotifier<QueryChange> changeNotifier = new ChangeNotifier<>();

    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

    private ResultSet previousResults;


    @NonNull
    private final AbstractQuery query;

    private final Object lock = new Object();

    private ListenerToken dbListenerToken;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    LiveQuery(@NonNull AbstractQuery query) {
        if (query == null) { throw new IllegalArgumentException("query cannot be null."); }
        this.query = query;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() { return "LiveQuery[" + query.toString() + "]"; }

    //---------------------------------------------
    // Implementation of DatabaseChangeListener
    //---------------------------------------------

    @Override
    public void changed(@NonNull DatabaseChange change) { update(); }

    //---------------------------------------------
    // protected methods
    //---------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    //---------------------------------------------
    // package
    //---------------------------------------------

    /**
     * Adds a change listener.
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    ListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        final ChangeListenerToken token = changeNotifier.addChangeListener(executor, listener);
        start(false);
        return token;
    }

    /**
     * Removes a change listener
     * <p>
     * NOTE: this method is synchronized with Query level.
     */
    void removeChangeListener(ListenerToken token) {
        if (changeNotifier.removeChangeListener(token) <= 0) { stop(); }
    }

    /**
     * Starts observing database changes and reports changes in the query result.
     */
    void start(boolean shouldClearResults) {
        final Database db = query.getDatabase();
        if (db == null) { throw new IllegalArgumentException("live query database cannot be null."); }

        synchronized (lock) {
            if (state.compareAndSet(State.STOPPED, State.STARTED)) {
                db.getActiveLiveQueries().add(this);
                dbListenerToken = db.addChangeListener(this);
            }
            else {
                // Here if the live query was already running.  This can happen in two ways:
                // 1) when adding another listener
                // 2) when the query parameters have changed.
                // In either case we may want to kick off a new query.
                // In the latter case the current query results are irrelevant.
                if (shouldClearResults) { releaseResultSetSynchronized(); }
            }
        }

        update();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void stop() {
        synchronized (lock) {
            final State oldState = state.getAndSet(State.STOPPED);
            if (State.STOPPED == oldState) { return; }

            final Database db = query.getDatabase();
            if (db != null) {
                db.getActiveLiveQueries().remove(this);
                db.removeChangeListener(dbListenerToken);
                dbListenerToken = null;
            }

            releaseResultSetSynchronized();
        }
    }

    private void update() {
        if (!state.compareAndSet(State.STARTED, State.SCHEDULED)) { return; }
        query.getDatabase().scheduleOnQueryExecutor(
            new Runnable() {
                @Override
                public void run() { refreshResults();  }
            },
            LIVE_QUERY_UPDATE_INTERVAL_MS);
    }

    // Runs on the query.database.queryExecutor
    // Assumes that call to `previousResults.refresh` is safe, even if previousResults has been freed.
    private void refreshResults() {
        try {
            final ResultSet prevResults;
            synchronized (lock) {
                if (!state.compareAndSet(State.SCHEDULED, State.STARTED)) { return; }
                prevResults = previousResults;
            }

            final ResultSet newResults = (prevResults == null) ? query.execute() : prevResults.refresh();
            Log.i(DOMAIN, "LiveQuery refresh: %s > %s", prevResults, newResults);
            if (newResults == null) { return; }

            boolean update = false;
            synchronized (lock) {
                if (state.get() != State.STOPPED) {
                    previousResults = newResults;
                    update = true;
                }
            }

            // Listeners may be notified even after the LiveQuery has been stopped.
            if (update) { changeNotifier.postChange(new QueryChange(query, newResults, null)); }
        }
        catch (CouchbaseLiteException err) {
            changeNotifier.postChange(new QueryChange(query, null, err));
        }
    }

    private void releaseResultSetSynchronized() {
        if (previousResults == null) { return; }
        previousResults.free();
        previousResults = null;
    }
}
