package com.couchbase.lite.internal.support;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

public class DefaultExecutor implements Executor {

    private static DefaultExecutor _instance;

    public static DefaultExecutor instance() {
        if (_instance == null)
            _instance = new DefaultExecutor();
        return _instance;
    }

    private DefaultExecutor() {
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable runnable) {
        handler.post(runnable);
    }
}
