package com.couchbase.touchdb.listener;

import java.util.Properties;

import Acme.Serve.Serve;

import com.couchbase.touchdb.TDServer;

@SuppressWarnings("serial")
public class TDHTTPServer extends Serve {

    public static final String TDSERVER_KEY = "TDServer";

    private Properties props;
    private TDServer server;
    private TDListener listener;

    public TDHTTPServer() {
        props = new Properties();
    }

    public void setServer(TDServer server) {
        this.server = server;
    }

    public void setListener(TDListener listener) {
        this.listener = listener;
    }

    public void setPort(int port) {
        props.put("port", port);
    }

    @Override
    public int serve() {
        //pass our custom properties in
        this.arguments = props;

        //pass in the tdserver to the servlet
        TDHTTPServlet servlet = new TDHTTPServlet();
        servlet.setServer(server);
        servlet.setListener(listener);

        this.addServlet("/", servlet);
        return super.serve();
    }

}
