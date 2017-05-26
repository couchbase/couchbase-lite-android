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
        else if (value instanceof Boolean)
            return value == Boolean.TRUE ? 1 : 0;
        else
            return defaultValue;
    }

    public static long toLong(Object value, long defaultValue) {
        if (value instanceof Number)
            return ((Number) value).longValue();
        else if (value instanceof Boolean)
            return value == Boolean.TRUE ? 1L : 0L;
        else
            return defaultValue;
    }

    public static float toFloat(Object value, float defaultValue) {
        if (value instanceof Number)
            return ((Number) value).floatValue();
        else if (value instanceof Boolean)
            return value == Boolean.TRUE ? 1.0F : 0.0F;
        else
            return defaultValue;
    }

    public static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        else if (value instanceof Boolean)
            return value == Boolean.TRUE ? 1.0 : 0.0;
        else
            return defaultValue;
    }
}
