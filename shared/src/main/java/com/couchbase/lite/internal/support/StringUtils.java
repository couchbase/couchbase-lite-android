package com.couchbase.lite.internal.support;

public class StringUtils {
    // NSString - stringByDeletingLastPathComponent
    // https://developer.apple.com/reference/foundation/nsstring/1411141-stringbydeletinglastpathcomponen
    public static String stringByDeletingLastPathComponent(final String str) {
        String path = str;
        int start = str.length() - 1;
        while (path.charAt(start) == '/')
            start--;

        int index = path.lastIndexOf('/', start);
        if (index != -1)
            path = path.substring(0, index);
        else
            path = "";


        if (path.length() == 0 && str.charAt(0) == '/')
            return "/";

        return path;
    }

    // NSString - lastPathComponent
    // https://developer.apple.com/reference/foundation/nsstring/1416528-lastpathcomponent
    public static String lastPathComponent(final String str) {
        String[] segments = str.split("/");
        if (segments != null && segments.length > 0)
            return segments[segments.length - 1];
        else
            return str;
    }
}
