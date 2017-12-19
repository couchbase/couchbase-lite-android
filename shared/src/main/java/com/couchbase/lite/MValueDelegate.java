/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.Encoder;
import com.couchbase.litecore.fleece.FLConstants;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLSharedKeys;
import com.couchbase.litecore.fleece.FLValue;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.concurrent.atomic.AtomicBoolean;

/* Internal delegate class for MValue - Mutable Fleece Value */
class MValueDelegate implements MValue.Delegate, FLConstants.FLValueType {
    @Override
    public Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt) {
        FLValue value = mv.getValue();
        int type = value.getType();
        switch (type) {
            case kFLArray:
                cacheIt.set(true);
                return mValueToArray(mv, parent);
            case kFLDict:
                cacheIt.set(true);
                return mValueToDictionary(mv, parent);
            default:
                return value.toObject(new SharedKeys(parent.getContext().getSharedKeys()));
        }
    }

    private static Object mValueToArray(MValue mv, MCollection parent) {
        if (parent != null && parent.getMutableChildren())
            return new MutableArray(mv, parent);
        else
            return new Array(mv, parent);
    }

    static Object createSpecialObjectOfType(String type, FLDict properties, DocContext context) {
        if (Blob.kC4ObjectType_Blob.equals(type))
            return createBlob(properties, context);
        return null;
    }

    static Object createBlob(FLDict properties, DocContext context) {
        return new Blob(context.getDatabase(),
                properties.toObject(new SharedKeys(context.getSharedKeys())));
    }

    static boolean isOldAttachment(FLDict flDict, FLSharedKeys sk) {
        FLValue flDigest = flDict.getSharedKey("digest", sk);
        FLValue flLength = flDict.getSharedKey("length", sk);
        FLValue flStub = flDict.getSharedKey("stub", sk);
        FLValue flRevPos = flDict.getSharedKey("revpos", sk);
        FLValue flContentType = flDict.getSharedKey("content_type", sk);
        return flDigest != null && flLength != null && flStub != null && flRevPos != null && flContentType != null;
    }

    private static Object mValueToDictionary(MValue mv, MCollection parent) {
        FLValue value = mv.getValue();
        FLDict flDict = value.asFLDict();
        DocContext context = (DocContext)parent.getContext();
        FLSharedKeys sk = context.getSharedKeys();
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

        if (parent != null && parent.getMutableChildren())
            return new MutableDictionary(mv, parent);
        else
            return new Dictionary(mv, parent);
    }

    @Override
    public MCollection collectionFromNative(Object object) {
        if (object instanceof Array)
            return ((Array)object).toMCollection();
        else if (object instanceof Dictionary)
            return ((Dictionary)object).toMCollection();
        else
            return null;
    }

    @Override
    public void encodeNative(Encoder enc, Object object) {
        if (object == null)
            enc.writeNull();
        else
            enc.writeObject(object);
    }
}
