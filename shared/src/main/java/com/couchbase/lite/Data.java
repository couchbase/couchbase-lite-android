package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLValue;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;


// CBLData.mm
public class Data {

    // + (id) fleeceValueToObject: (FLValue)value
    //                      c4doc: (CBLC4Document*)c4doc
    //                  database: (CBLDatabase*)database
    public static Object fleeceValueToObject(FLValue value, SharedKeys sk) {
        if (value == null) return null;
        switch (value.getType()) {
            case kFLArray:
                // TODO:
                return null;
            case kFLDict:
                // TODO:
                return null;
            default:
                return SharedKeys.valueToObject(value, sk);
        }
    }
}
