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

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.couchbase.lite.internal.fleece.FLConstants;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.DateUtils;


final class Fleece implements FLConstants.FLValueType {
    private static final String LIST_OF_SUPPORTED_TYPES
        = "MutableDictionary, Dictionary, MutableArray, Array, Map, List, Date, String, Number, Boolean, Blob or null";

    static boolean valueWouldChange(Object newValue, MValue oldValue, MCollection container) {
        // As a simplification we assume that array and dict values are always different, to avoid
        // a possibly expensive comparison.
        final int oldType = oldValue.getValue() != null ? oldValue.getValue().getType() : kFLUndefined;
        if (oldType == kFLUndefined || oldType == kFLDict || oldType == kFLArray) { return true; }

        if (newValue instanceof Array || newValue instanceof Dictionary) { return true; }
        else {
            final Object oldVal = oldValue.asNative(container);
            return newValue != null ? !newValue.equals(oldVal) : oldVal != null;
        }
    }

    @SuppressWarnings("unchecked")
    static Object toCBLObject(Object value) {
        if (value instanceof MutableDictionary) {
            return value;
        }
        else if (value instanceof Dictionary) {
            return ((Dictionary) value).toMutable();
        }
        else if (value instanceof MutableArray) {
            return value;
        }
        else if (value instanceof Array) {
            return ((Array) value).toMutable();
        }
        else if (value instanceof Map) {
            return new MutableDictionary((Map<String, Object>) value);
        }
        else if (value instanceof List) {
            return new MutableArray((List<Object>) value);
        }
        else if (value instanceof Date) {
            return DateUtils.toJson((Date) value);
        }
        else {
            if (!(value == null ||
                value instanceof String ||
                value instanceof Number ||
                value instanceof Boolean ||
                value instanceof Blob)) {
                throw new IllegalArgumentException(String.format(
                    Locale.ENGLISH,
                    "%s is not a valid type. You may only pass %s.",
                    value.getClass().getSimpleName(),
                    LIST_OF_SUPPORTED_TYPES));
            }
        }
        return value;
    }

    static Object toObject(Object value) {
        if (value == null) { return null; }
        else if (value instanceof Dictionary) { return ((Dictionary) value).toMap(); }
        else if (value instanceof Array) { return ((Array) value).toList(); }
        else { return value; }
    }
}
