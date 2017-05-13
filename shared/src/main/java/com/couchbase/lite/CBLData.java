package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLArray;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;


// CBLData.mm
public class CBLData {

    /*package*/static Object convert(Object value, ObjectChangeListener listener){
        //TODO
        return value;
    }

    /*package*/static boolean toBoolean(Object object){
        if(object==null)
            return false;
        else{
            if(object instanceof Number)
                return ((Number)object).doubleValue() != 0.0;
            else
                return true;
        }
    }

    // + (id) fleeceValueToObject: (FLValue)value
    //                      c4doc: (CBLC4Document*)c4doc
    //                  database: (CBLDatabase*)database
    /*package*/ static Object fleeceValueToObject(FLValue value, CBLC4Doc c4doc, Database database) {
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
