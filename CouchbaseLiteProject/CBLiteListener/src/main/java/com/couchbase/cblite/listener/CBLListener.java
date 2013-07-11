package com.couchbase.cblite.listener;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ScheduledExecutorService;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;

public class CBLListener implements Runnable {

    private Thread thread;
    private CBLServer server;
    private CBLHTTPServer httpServer;
    public static final String TAG = "CBLListener";
    private int listenPort;

    //static inializer to ensure that cblite:// URLs are handled properly
    {
        CBLURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    /**
     * CBLListener constructor
     *
     * @param server the CBLServer instance
     * @param suggestedPort the suggested port to use.  if not available, will hunt for a new port.
     *                      and this port can be discovered by calling getListenPort()
     */
    public CBLListener(CBLServer server, int suggestedPort) {
        this.server = server;
        this.httpServer = new CBLHTTPServer();
        this.httpServer.setServer(server);
        this.httpServer.setListener(this);
        this.listenPort = discoverEmptyPort(suggestedPort);
        this.httpServer.setPort(this.listenPort);
    }

    /**
     * Hunt for an empty port starting from startPort by binding a server
     * socket until it succeeds w/o getting an exception.  Once found, close
     * the server socket so that port is available.
     *
     * Caveat: yes, there is a tiny race condition in the sense that the
     * caller could receive a port that was bound by another thread
     * before it has a chance to bind)
     *
     * @param startPort - the port to start hunting at, eg: 5984
     * @return the port that was bound.  (or a runtime exception is thrown)
     */
    public int discoverEmptyPort(int startPort) {
        for (int curPort=startPort; curPort<65536; curPort++) {
            try {
                ServerSocket socket = new ServerSocket(curPort);
                socket.close();
                return curPort;
            } catch (IOException e) {
                Log.d(CBLListener.TAG, "Could not bind to port: " + curPort + ".  Trying another port.");
            }

        }
        throw new RuntimeException("Could not find empty port starting from: " + startPort);
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

    public int getListenPort() {
        return listenPort;
    }
}
