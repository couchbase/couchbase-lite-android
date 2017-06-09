package com.couchbase.lite.internal.support;


import java.net.URI;

public class URIUtils {

    public static String getUsername(URI uri) {
        if (uri == null ||
                uri.getUserInfo() == null ||
                uri.getUserInfo().trim().length() == 0 ||
                uri.getUserInfo().trim().equals(":"))
            return null;
        return uri.getUserInfo().trim().split(":")[0];
    }

    public static String getPassword(URI uri) {
        if (uri == null ||
                uri.getUserInfo() == null ||
                uri.getUserInfo().trim().length() == 0 ||
                uri.getUserInfo().trim().equals(":"))
            return null;
        String[] userInfo = uri.getUserInfo().trim().split(":");
        if (userInfo.length == 2)
            return userInfo[1];
        else
            return null;
    }
}
