package com.couchbase.touchdb.support;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;

/**
 * Utility that queues up objects until the queue fills up or a time interval elapses,
 * then passes all the objects at once to a client-supplied processor block.
 */
public class TDBatcher<T> {

    private Handler handler;
    private int capacity;
    private int delay;
    private List<T> inbox;
    private TDBatchProcessor<T> processor;

    private Runnable processNowRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                processNow();
            } catch(Exception e) {
                // we don't want this to crash the batcher
                Log.e(TDDatabase.TAG, "TDBatchProcessor throw exception", e);
            }
        }
    };

    public TDBatcher(Handler handler, int capacity, int delay, TDBatchProcessor<T> processor) {
        this.handler = handler;
        this.capacity = capacity;
        this.delay = delay;
        this.processor = processor;
    }

    public void processNow() {
        List<T> toProcess = null;
        synchronized(this) {
            if(inbox == null || inbox.size() == 0) {
                return;
            }
            toProcess = inbox;
            inbox = null;
        }
        if(toProcess != null) {
            processor.process(toProcess);
        }
    }

    public void queueObject(T object) {
        synchronized(this) {
            if(inbox != null && inbox.size() >= capacity) {
                flush();
            }
            if(inbox == null) {
                inbox = new ArrayList<T>();
                if(handler != null) {
                    handler.postDelayed(processNowRunnable, delay);
                }
            }
            inbox.add(object);
        }
    }

    public void flush() {
        synchronized(this) {
            if(inbox != null) {
                handler.removeCallbacks(processNowRunnable);
                processNow();
            }
        }
    }

    public int count() {
        synchronized(this) {
            if(inbox == null) {
                return 0;
            }
            return inbox.size();
        }
    }

    public void close() {

    }

}
