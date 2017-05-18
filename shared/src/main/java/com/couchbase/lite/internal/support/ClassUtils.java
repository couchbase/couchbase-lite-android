package com.couchbase.lite.internal.support;


import static android.R.attr.value;

public class ClassUtils {
    public static <T> T cast(Object obj, Class<T> clazz) {
        if (obj != null && !clazz.isInstance(obj))
            return null;
        return (T) obj;
    }

    public static int toInt(Object obj, int defaultValue) {
        if (obj instanceof Number)
            return ((Number) value).intValue();
        else
            return defaultValue;
    }

    public static long toLong(Object obj, long defaultValue) {
        if (obj instanceof Number)
            return ((Number) value).longValue();
        else
            return defaultValue;
    }

    public static float toFloat(Object obj, float defaultValue) {
        if (obj instanceof Number)
            return ((Number) value).floatValue();
        else
            return defaultValue;
    }

    public static double toDouble(Object obj, double defaultValue) {
        if (obj instanceof Number)
            return ((Number) value).doubleValue();
        else
            return defaultValue;
    }
}
