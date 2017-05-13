package com.couchbase.lite;

import com.couchbase.lite.internal.document.RemovedValue;
import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLArray;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;


// CBLData.mm
public class CBLData {
    /* package */
    static Object convert(Object value, ObjectChangeListener listener) {
        if (value == null) {
            return RemovedValue.INSTANCE;// Represent removed key
        } else if (value instanceof Dictionary) {
            ((Dictionary) value).addChangeListener(listener);
            return value;
        } else if (value instanceof Array) {
            ((Array) value).addChangeListener(listener);
            return value;
        } else if (value instanceof ReadOnlyDictionary) {
            ReadOnlyDictionary readOnly = (ReadOnlyDictionary) value;
            Dictionary dict = new Dictionary(readOnly.getData());
            dict.addChangeListener(listener);
            return dict;
        } else if (value instanceof ReadOnlyArray) {
            ReadOnlyArray readOnly = (ReadOnlyArray) value;
            Array array = new Array(readOnly.getData());
            array.addChangeListener(listener);
            return array;
        } else if (value instanceof Map) {
            Dictionary dict = new Dictionary((Map) value);
            dict.addChangeListener(listener);
            return dict;
        } else if (value instanceof List) {
            Array array = new Array((List) value);
            array.addChangeListener(listener);
            return array;
        } else if (value instanceof Date) {
            return DateUtils.toJson((Date) value);
        }
        return value;
    }

    /*package*/
    static boolean toBoolean(Object object) {
        if (object == null)
            return false;
        else {
            if (object instanceof Number)
                return ((Number) object).doubleValue() != 0.0;
            else
                return true;
        }
    }

    // + (id) fleeceValueToObject: (FLValue)value
    //                      c4doc: (CBLC4Document*)c4doc
    //                  database: (CBLDatabase*)database
    /*package*/
    static Object fleeceValueToObject(FLValue value, CBLC4Doc c4doc, Database database) {
        if (value == null) return null;
        switch (value.getType()) {
            case kFLArray: {
                FLArray flArray = value.asFLArray();
                CBLFLArray data = new CBLFLArray(flArray, c4doc, database);
                return new Array(data);
            }
            case kFLDict: {
                FLDict flDict = value.asFLDict();
                //TODO: for Blob
                CBLFLDict data = new CBLFLDict(flDict, c4doc, database);
                return new Dictionary(data);
            }
            default: {
                return SharedKeys.valueToObject(value, database.getSharedKeys());
            }
        }
    }
}
