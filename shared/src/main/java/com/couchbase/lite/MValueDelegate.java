//
// MValueDelegate.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.internal.fleece.Encoder;
import com.couchbase.lite.internal.fleece.FLConstants;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;


/* Internal delegate class for MValue - Mutable Fleece Value */
final class MValueDelegate implements MValue.Delegate, FLConstants.FLValueType {

    @Override
    public Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt) {
        final FLValue value = mv.getValue();
        switch (value.getType()) {
            case kFLArray:
                cacheIt.set(true);
                return mValueToArray(mv, parent);
            case kFLDict:
                cacheIt.set(true);
                return mValueToDictionary(mv, parent);
            case kFLData:
                return new Blob("application/octet-stream", value.asData());
            default:
                return value.asObject();
        }
    }

    @Override
    public MCollection collectionFromNative(Object object) {
        if (object instanceof Array) { return ((Array) object).toMCollection(); }
        else if (object instanceof Dictionary) { return ((Dictionary) object).toMCollection(); }
        else { return null; }
    }

    @Override
    public void encodeNative(Encoder enc, Object object) {
        if (object == null) { enc.writeNull(); }
        else { enc.writeObject(object); }
    }

    private Object mValueToArray(MValue mv, MCollection parent) {
        if (parent != null && parent.hasMutableChildren()) { return new MutableArray(mv, parent); }
        else { return new Array(mv, parent); }
    }

    private Object createSpecialObjectOfType(String type, FLDict properties, DocContext context) {
        if (Blob.kBlobType.equals(type)) { return createBlob(properties, context); }
        return null;
    }

    private Object createBlob(FLDict properties, DocContext context) {
        return new Blob(context.getDatabase(), properties.asDict());
    }

    private boolean isOldAttachment(FLDict flDict) {
        return flDict.get("digest") != null
            && flDict.get("length") != null
            && flDict.get("stub") != null
            && flDict.get("revpos") != null;
    }

    private Object mValueToDictionary(MValue mv, MCollection parent) {
        final FLDict flDict = mv.getValue().asFLDict();
        final DocContext context = (DocContext) parent.getContext();
        final FLValue flType = flDict.get(Blob.kMetaPropertyType);
        final String type = flType != null ? flType.asString() : null;
        if (type == null) {
            if (isOldAttachment(flDict)) { return createBlob(flDict, context); }
        }
        else {
            final Object obj = createSpecialObjectOfType(type, flDict, context);
            if (obj != null) { return obj; }
        }

        if (parent.hasMutableChildren()) { return new MutableDictionary(mv, parent); }
        else { return new Dictionary(mv, parent); }
    }
}
