package com.couchbase.touchdb.router;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;


public class TDURLStreamHandlerFactor implements URLStreamHandlerFactory {

    public static final String SCHEME = "touchdb";

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if(SCHEME.equals(protocol)) {
            return new TDURLHandler();
        }
        return null;
    }

}
