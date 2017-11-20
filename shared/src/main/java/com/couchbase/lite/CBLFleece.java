package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.FLConstants;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLSharedKeys;
import com.couchbase.litecore.fleece.FLValue;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MContext;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.internal.support.ClassUtils.cast;

public class CBLFleece implements FLConstants.FLValueType {

    // Instantiate an Java object for a Fleece dictionary with an "@type" key. */
    static Object createSpecialObjectOfType(String type, FLDict properties, MContext context) {
        if (Blob.kC4ObjectType_Blob.equals(type)) {
            return new Blob((Database) context.getNative(), properties.toObject(new SharedKeys(context.sharedKeys())));
        }
        return null;
    }

    // to call from native
    // static Object MValue_toDictionary(MValue mv, MCollection parent)
    static Object MValue_toDictionary(long mv, long parent) {
        return MValue_toDictionary(new MValue(mv, true), new MCollection(parent, true));
    }

    // to call from native
    // static Object MValue_toDictionary(MValue mv, MCollection parent)
    static Object MValue_toDictionary(Object mv, Object parent) {
        return MValue_toDictionary((MValue) mv, (MCollection) parent);
    }

    static Object MValue_toDictionary(MValue mv, MCollection parent) {
        FLValue value = mv.value();
        MContext context =parent.context();
        FLSharedKeys sk = context.sharedKeys();
        FLValue flType= value.asFLDict().getSharedKey(Blob.kC4ObjectTypeProperty, sk);
        String type = flType!=null?flType.asString():null;
        if(type != null){
            Object obj = createSpecialObjectOfType(type, value.asFLDict(), context);
            if(obj != null)
                return obj;
        }
        return new Dictionary(mv, parent);
    }

    // to call from native
    // static Object MValue_toDictionary(MValue mv, MCollection parent)
    static Object MValue_toArray(long mv, long parent) {
        return MValue_toArray(new MValue(mv, true), new MCollection(parent, true));
    }

    // to call from native
    // static Object MValue_toDictionary(MValue mv, MCollection parent)
    static Object MValue_toArray(Object mv, Object parent) {
        return MValue_toArray((MValue) mv, (MCollection) parent);
    }

    static Object MValue_toArray(MValue mv, MCollection parent) {
        return new Array(mv, parent);
    }








    public static boolean asBool(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asBool();
        else
            return toBoolean(val.asNative(container));
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


    static Number getNumber(Object value) {
        // special handling for Boolean
        if (value != null && value instanceof Boolean)
            return value == Boolean.TRUE ? Integer.valueOf(1) : Integer.valueOf(0);
        else
            return cast(value, Number.class);
    }

    public static int asInteger(MValue val, MCollection container) {
        if (val.value() != null)
            return (int) val.value().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.intValue() : 0;
        }
    }

    public static long asLong(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.longValue() : 0L;
        }
    }

    public static float asFloat(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asFloat();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.floatValue() : 0L;
        }
    }

    public static double asDouble(MValue val, MCollection container) {
        if (val.value() != null)
            return val.value().asDouble();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.doubleValue() : 0L;
        }
    }




    public static boolean valueWouldChange(Object newValue, MValue oldValue, MCollection container) {
        // As a simplification we assume that array and dict values are always different, to avoid
        // a possibly expensive comparison.
        int oldType = oldValue.value() != null ? oldValue.value().getType() : kFLUndefined;
        if (oldType == kFLUndefined || oldType == kFLDict || oldType == kFLArray)
            return true;


        if (newValue instanceof ReadOnlyArray || newValue instanceof ReadOnlyDictionary)
            return true;
        else {
            Object oldVal = oldValue.asNative(container);
            return newValue != null ? !newValue.equals(oldVal) : oldVal != null;
        }
    }


    public static Object toCBLObject(Object value) {
        if (value instanceof Dictionary) {
            return value;
        } else if (value instanceof Array) {
            return value;
//        } else if (value instanceof ReadOnlyDictionary) {
//            ReadOnlyDictionary readOnly = (ReadOnlyDictionary) value;
//            Dictionary dict = new Dictionary(readOnly.getData());
//            return dict;
//        } else if (value instanceof ReadOnlyArray) {
//            ReadOnlyArray readOnly = (ReadOnlyArray) value;
//            Array array = new Array(readOnly.getData());
//            return array;
        } else if (value instanceof Map) {
            Dictionary dict = new Dictionary((Map) value);
            return dict;
        } else if (value instanceof List) {
            Array array = new Array((List) value);
            return array;
        } else if (value instanceof Date) {
            return DateUtils.toJson((Date) value);
        } else {
            if (!(value == null ||
                    /*value == RemovedValue.INSTANCE ||*/
                    value instanceof String ||
                    value instanceof Number ||
                    value instanceof Boolean ||
                    value instanceof Blob)) {
                throw new IllegalArgumentException("Unsupported value type. value = " + value.getClass().getSimpleName());
            }
        }
        return value;
    }

    public static Object toObject(Object value) {
        if (value == null)
            return null;
        else if (value instanceof ReadOnlyDictionary)
            return ((ReadOnlyDictionary) value).toMap();
        else if (value instanceof ReadOnlyArray)
            return ((ReadOnlyArray) value).toList();
        else
            return value;
    }
}
