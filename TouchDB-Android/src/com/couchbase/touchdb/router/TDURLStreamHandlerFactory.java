package com.couchbase.touchdb.router;

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;


public class TDURLStreamHandlerFactory implements URLStreamHandlerFactory {

    public static final String SCHEME = "touchdb";

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if(SCHEME.equals(protocol)) {
            return new TDURLHandler();
        }
        return null;
    }

    public static void registerSelfIgnoreError() {
        try {
            URL.setURLStreamHandlerFactory(new TDURLStreamHandlerFactory());
        } catch (Error e) {
            //usually you should never catch an Error
            //but I can't see how to avoid this
        }
    }

}
