package com.couchbase.lite.internal.utils;

public class ClassUtils {
    private ClassUtils() {

    }

    public static <T> T cast(Object obj, Class<T> clazz) {
        if (obj != null && !clazz.isInstance(obj))
            return null;
        return (T) obj;
    }
}
