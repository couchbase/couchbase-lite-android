package com.couchbase.lite;

import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import static com.couchbase.lite.internal.utils.ClassUtils.cast;

final class CBLConverter {
    static Number getNumber(Object value) {
        // special handling for Boolean
        if (value != null && value instanceof Boolean)
            return value == Boolean.TRUE ? Integer.valueOf(1) : Integer.valueOf(0);
        else
            return cast(value, Number.class);
    }

    static int asInteger(MValue val, MCollection container) {
        if (val.getValue() != null)
            return (int) val.getValue().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.intValue() : 0;
        }
    }

    static long asLong(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.longValue() : 0L;
        }
    }

    static float asFloat(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asFloat();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.floatValue() : 0L;
        }
    }

    static double asDouble(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asDouble();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.doubleValue() : 0L;
        }
    }
}
