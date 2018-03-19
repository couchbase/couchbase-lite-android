//
// Fleece.java
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

import com.couchbase.lite.internal.utils.DateUtils;
import com.couchbase.litecore.fleece.FLConstants;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Fleece implements FLConstants.FLValueType {
    static String LIST_OF_SUPPORTED_TYPES = "MutableDictionary, Dictionary, MutableArray, Array, Map, List, Date, String, Number, Boolean, Blob or null";

    static boolean valueWouldChange(Object newValue, MValue oldValue, MCollection container) {
        // As a simplification we assume that array and dict values are always different, to avoid
        // a possibly expensive comparison.
        int oldType = oldValue.getValue() != null ? oldValue.getValue().getType() : kFLUndefined;
        if (oldType == kFLUndefined || oldType == kFLDict || oldType == kFLArray)
            return true;

        if (newValue instanceof Array || newValue instanceof Dictionary)
            return true;
        else {
            Object oldVal = oldValue.asNative(container);
            return newValue != null ? !newValue.equals(oldVal) : oldVal != null;
        }
    }

    static Object toCBLObject(Object value) {
        if (value instanceof MutableDictionary) {
            return value;
        } else if (value instanceof Dictionary) {
            return ((Dictionary) value).toMutable();
        } else if (value instanceof MutableArray) {
            return value;
        } else if (value instanceof Array) {
            return ((Array) value).toMutable();
        } else if (value instanceof Map) {
            MutableDictionary dict = new MutableDictionary((Map) value);
            return dict;
        } else if (value instanceof List) {
            MutableArray array = new MutableArray((List) value);
            return array;
        } else if (value instanceof Date) {
            return DateUtils.toJson((Date) value);
        } else {
            if (!(value == null ||
                    value instanceof String ||
                    value instanceof Number ||
                    value instanceof Boolean ||
                    value instanceof Blob)) {
                String msg = String.format(Locale.ENGLISH,
                        "%s is not a valid type. You may only pass %s.",
                        value.getClass().getSimpleName(), LIST_OF_SUPPORTED_TYPES);
                throw new IllegalArgumentException(msg);
            }
        }
        return value;
    }

    static Object toObject(Object value) {
        if (value == null)
            return null;
        else if (value instanceof Dictionary)
            return ((Dictionary) value).toMap();
        else if (value instanceof Array)
            return ((Array) value).toList();
        else
            return value;
    }
}
