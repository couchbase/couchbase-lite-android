package com.couchbase.lite;

import com.couchbase.lite.internal.document.RemovedValue;
import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.FLArray;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;

// CBLData.mm
class CBLData {
    static Object convert(Object value) {
        if (value instanceof Dictionary) {
            return value;
        } else if (value instanceof Array) {
            return value;
        } else if (value instanceof ReadOnlyDictionary) {
            ReadOnlyDictionary readOnly = (ReadOnlyDictionary) value;
            Dictionary dict = new Dictionary(readOnly.getData());
            return dict;
        } else if (value instanceof ReadOnlyArray) {
            ReadOnlyArray readOnly = (ReadOnlyArray) value;
            Array array = new Array(readOnly.getData());
            return array;
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
                    value == RemovedValue.INSTANCE ||
                    value instanceof String ||
                    value instanceof Number ||
                    value instanceof Boolean ||
                    value instanceof Blob)) {
                throw new IllegalArgumentException("Unsupported value type. value = " + value.getClass().getSimpleName());
            }
        }
        return value;
    }

    static boolean toBoolean(Object object) {
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

    // + (id) fleeceValueToObject: (FLValue)value
    //                      c4doc: (CBLC4Document*)c4doc
    //                  database: (CBLDatabase*)database
    static Object fleeceValueToObject(FLValue value, CBLC4Doc c4doc, Database database) {
        if (value == null) return null;
        switch (value.getType()) {
            case kFLArray: {
                FLArray flArray = value.asFLArray();
                CBLFLArray data = new CBLFLArray(flArray, c4doc, database);
                return new Array(data);
            }
            case kFLDict: {
                if (value.getType() != kFLDict)
                    throw new IllegalStateException("value is not kFLDict");
                FLDict flDict = value.asFLDict();
                String type = SharedKeys.getValue(flDict, Blob.kC4ObjectTypeProperty, database.getSharedKeys()).asString();
                if (type == null) {
                    return new Dictionary(new CBLFLDict(flDict, c4doc, database));
                } else { // type == "blob"
                    Object result = SharedKeys.valueToObject(value, database.getSharedKeys());
                    return dictionaryToCBLObject((Map<String, Object>) result, database);
                }
            }
            default: {
                return SharedKeys.valueToObject(value, database.getSharedKeys());
            }
        }
    }

    static Object fleeceValueToObject(FLValue value, CBLFLDataSource flDataSource, Database database) {
        if (value == null) return null;
        switch (value.getType()) {
            case kFLArray: {
                FLArray flArray = value.asFLArray();
                CBLFLArray data = new CBLFLArray(flArray, flDataSource, database);
                return new Array(data);
            }
            case kFLDict: {
                if (value.getType() != kFLDict)
                    throw new IllegalStateException("value is not kFLDict");
                FLDict flDict = value.asFLDict();
                String type = SharedKeys.getValue(flDict, Blob.kC4ObjectTypeProperty, database.getSharedKeys()).asString();
                if (type == null) {
                    return new Dictionary(new CBLFLDict(flDict, flDataSource, database));
                } else { // type == "blob"
                    Object result = SharedKeys.valueToObject(value, database.getSharedKeys());
                    return dictionaryToCBLObject((Map<String, Object>) result, database);
                }
            }
            default: {
                return SharedKeys.valueToObject(value, database.getSharedKeys());
            }
        }
    }

    private static Object dictionaryToCBLObject(Map<String, Object> dict, Database database) {
        String type = (String) dict.get(Blob.kC4ObjectTypeProperty);
        if (type != null && type.equals(Blob.kC4ObjectType_Blob)) {
            return new Blob(database, dict);
        }
        return null;
    }
}
