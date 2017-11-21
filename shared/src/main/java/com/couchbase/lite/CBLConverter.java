package com.couchbase.lite;

import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import static com.couchbase.lite.internal.support.ClassUtils.cast;

public class CBLConverter {
    static boolean asBool(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asBool();
        else
            return toBoolean(val.asNative(container));
    }

    static Number getNumber(Object value) {
        // special handling for Boolean
        if (value != null && value instanceof Boolean)
            return value == Boolean.TRUE ? Integer.valueOf(1) : Integer.valueOf(0);
        else
            return cast(value, Number.class);
    }

    static int asInteger(MValue val, MCollection container) {
        if (val.value() != null)
            return (int) val.value().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.intValue() : 0;
        }
    }

    static long asLong(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.longValue() : 0L;
        }
    }

    static float asFloat(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asFloat();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.floatValue() : 0L;
        }
    }

    static double asDouble(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asDouble();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.doubleValue() : 0L;
        }
    }

    private static boolean toBoolean(Object object) {
        if (object == null)
            return false;
        else {
            if (object instanceof Boolean)
                return ((Boolean) object).booleanValue();
            else if (object instanceof Number)
                return ((Number) object).doubleValue() != 0.0;
            else
                return true;
        }
    }
}
