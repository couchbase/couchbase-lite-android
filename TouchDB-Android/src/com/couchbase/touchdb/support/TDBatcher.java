package com.couchbase.touchdb.support;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;

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
            processNow();
        }
    };

    public TDBatcher(int capacity, int delay, TDBatchProcessor<T> processor) {
        this.handler = new Handler();
        this.capacity = capacity;
        this.delay = delay;
        this.processor = processor;
    }

    public void processNow() {
        if(inbox == null || inbox.size() == 0) {
            return;
        }
        List<T> toProcess = inbox;
        inbox = null;
        processor.process(toProcess);
    }

    public void queueObject(T object) {
        if(inbox != null && inbox.size() >= capacity) {
            flush();
        }
        if(inbox == null) {
            inbox = new ArrayList<T>();
            handler.postDelayed(processNowRunnable, 2 * 1000);
        }
        inbox.add(object);
    }

    public void flush() {
        if(inbox != null) {
            handler.removeCallbacks(processNowRunnable);
            processNow();
        }
    }

    public int count() {
        return inbox.size();
    }

}
