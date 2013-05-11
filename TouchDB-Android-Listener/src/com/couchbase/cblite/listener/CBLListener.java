package com.couchbase.cblite.listener;

import java.util.concurrent.ScheduledExecutorService;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;

public class CBLListener implements Runnable {

    private Thread thread;
    private CBLServer server;
    private CBLHTTPServer httpServer;

    //static inializer to ensure that cblite:// URLs are handled properly
    {
        CBLURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    public CBLListener(CBLServer server, int port) {
        this.server = server;
        this.httpServer = new CBLHTTPServer();
        this.httpServer.setServer(server);
        this.httpServer.setListener(this);
        this.httpServer.setPort(port);
    }

    @Override
    public void run() {
        httpServer.serve();
    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        httpServer.notifyStop();
    }

    public void onServerThread(Runnable r) {
        ScheduledExecutorService workExecutor = server.getWorkExecutor();
        workExecutor.submit(r);
    }

    public String getStatus() {
        String status = this.httpServer.getServerInfo();
        return status;
    }

}
