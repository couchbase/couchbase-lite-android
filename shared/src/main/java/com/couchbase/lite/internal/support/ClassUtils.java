package com.couchbase.lite.internal.support;

public class ClassUtils {
    public static <T> T cast(Object obj, Class<T> clazz) {
        if (obj != null && !clazz.isInstance(obj))
            return null;
        return (T) obj;
    }

    public static int toInt(Object value, int defaultValue) {
        if (value instanceof Number)
            return ((Number) value).intValue();
        else
            return defaultValue;
    }

    public static long toLong(Object value, long defaultValue) {
        if (value instanceof Number)
            return ((Number) value).longValue();
        else
            return defaultValue;
    }

    public static float toFloat(Object value, float defaultValue) {
        if (value instanceof Number)
            return ((Number) value).floatValue();
        else
            return defaultValue;
    }

    public static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        else
            return defaultValue;
    }
}
