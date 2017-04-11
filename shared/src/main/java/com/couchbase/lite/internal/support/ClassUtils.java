package com.couchbase.lite.internal.support;


public class ClassUtils {
    public static <T> T cast(Object obj, Class<T> clazz) {
        if (obj != null && !clazz.isInstance(obj))
            return null;
        return (T) obj;
    }
}
