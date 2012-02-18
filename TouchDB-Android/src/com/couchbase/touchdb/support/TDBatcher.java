package com.couchbase.touchdb.support;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;

/**
 * Utility that queues up objects until the queue fills up or a time interval elapses,
 * then passes all the objects at once to a client-supplied processor block.
 */
public class TDBatcher<T> {

    private HandlerThread handlerThread;
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

    public TDBatcher(int capacity, int delay, TDBatchProcessor<T> processor) {
        //first start a handler thread
        String threadName = Thread.currentThread().getName();
        handlerThread = new HandlerThread("TDBatcher HandlerThread for " + threadName);
        handlerThread.start();
        //Get the looper from the handlerThread
        Looper looper = handlerThread.getLooper();
        //Create a new handler - passing in the looper for it to use
        this.handler = new Handler(looper);
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
            if(handler != null) {
                handler.postDelayed(processNowRunnable, 2 * 1000);
            }
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
        if(inbox == null) {
            return 0;
        }
        return inbox.size();
    }

    public void close() {
        //Shut down the HandlerThread
        handlerThread.quit();
        handlerThread = null;
        handler = null;
    }

}
