package com.couchbase.lite.internal.support;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtils {
    private static final String TAG = "DateUtils";

    private static SimpleDateFormat sdf;

    static {
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String toJson(Date date) {
        return sdf.format(date);
    }

    public static Date fromJson(String json) {
        if (json == null) return null;
        try {
            return sdf.parse(json);
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse JSON string: %s", e, json);
            return null;
        }
    }
}
