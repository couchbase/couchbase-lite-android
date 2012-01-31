package com.couchbase.touchdb.listener;

import java.net.URL;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactor;

public class TDListener implements Runnable {

    private Thread thread;
    private TDServer server;
    private TDHTTPServer httpServer;

    //static inializer to ensure that touchdb:// URLs are handled properly
    {
        URL.setURLStreamHandlerFactory(new TDURLStreamHandlerFactor());
    }

    public TDListener(TDServer server, int port) {
        this.server = server;
        this.httpServer = new TDHTTPServer();
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

}
