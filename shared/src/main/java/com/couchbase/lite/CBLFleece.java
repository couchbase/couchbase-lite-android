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

public class CBLFleece implements FLConstants.FLValueType {
    // Blob
    static Object createSpecialObjectOfType(String type, FLDict properties, MContext context) {
        if (Blob.kC4ObjectType_Blob.equals(type))
            return createBlob(properties, context);
        return null;
    }

    static Object createBlob(FLDict properties, MContext context) {
        return new Blob((Database) context.getNative(), properties.toObject(new SharedKeys(context.sharedKeys())));
    }

    static boolean isOldAttachment(FLDict flDict, FLSharedKeys sk) {
        FLValue flDigest = flDict.getSharedKey("digest", sk);
        FLValue flLength = flDict.getSharedKey("length", sk);
        FLValue flStub = flDict.getSharedKey("stub", sk);
        FLValue flRevPos = flDict.getSharedKey("revpos", sk);
        FLValue flContentType = flDict.getSharedKey("content_type", sk);
        return flDigest != null && flLength != null && flStub != null && flRevPos != null && flContentType != null;
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
        FLDict flDict = value.asFLDict();
        MContext context = parent.context();
        FLSharedKeys sk = context.sharedKeys();
        FLValue flType = flDict.getSharedKey(Blob.kC4ObjectTypeProperty, sk);
        String type = flType != null ? flType.asString() : null;
        if (type != null) {
            Object obj = createSpecialObjectOfType(type, flDict, context);
            if (obj != null)
                return obj;
        } else {
            if (isOldAttachment(flDict, sk))
                return createBlob(flDict, context);
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


    static boolean valueWouldChange(Object newValue, MValue oldValue, MCollection container) {
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

    static Object toCBLObject(Object value) {
        if (value instanceof Dictionary) {
            return value;
        } else if (value instanceof Array) {
            return value;
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
                    value instanceof String ||
                    value instanceof Number ||
                    value instanceof Boolean ||
                    value instanceof Blob)) {
                throw new IllegalArgumentException("Unsupported value type. value = " + value.getClass().getSimpleName());
            }
        }
        return value;
    }

    static Object toObject(Object value) {
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
