package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Map;

import static com.couchbase.litecore.SharedKeys.getValue;

// CBLData.mm
public class CBLData {
    private static Object dictionaryToCBLObject(Map<String, Object> dict, Database database) {
        if (isBlob(dict) || isOldAttachment(dict)) {
            return new Blob(database, dict);
        }
        return null;
    }

    private static boolean isBlob(FLDict flDict, Database database) {
        FLValue flType = getValue(flDict, Blob.kC4ObjectTypeProperty, database.getSharedKeys());
        String type = flType != null ? flType.asString() : null;
        return type != null && type.equals(Blob.kC4ObjectType_Blob);
    }

    private static boolean isBlob(Map<String, Object> dict) {
        String type = (String) dict.get(Blob.kC4ObjectTypeProperty);
        return type != null && type.equals(Blob.kC4ObjectType_Blob);
    }

    // NOTE: CBL v1.x does not add `"@type": "blob"` entry in the dictionary. Following is
    // a workaround to detect CBL v1.x attachment.

    private static boolean isOldAttachment(FLDict flDict, Database database) {
        FLValue flDigest = getValue(flDict, "digest", database.getSharedKeys());
        FLValue flLength = getValue(flDict, "length", database.getSharedKeys());
        FLValue flStub = getValue(flDict, "stub", database.getSharedKeys());
        FLValue flRevPos = getValue(flDict, "revpos", database.getSharedKeys());
        FLValue flContentType = getValue(flDict, "content_type", database.getSharedKeys());
        return flDigest != null && flLength != null && flStub != null && flRevPos != null && flContentType != null;
    }

    private static boolean isOldAttachment(Map<String, Object> dict) {
        Object digest = dict.get("digest");
        Object length = dict.get("length");
        Object stub = dict.get("stub");
        Object revpos = dict.get("revpos");
        Object contentType = dict.get("content_type");
        return digest != null && length != null && stub != null && revpos != null && contentType != null;
    }
}
