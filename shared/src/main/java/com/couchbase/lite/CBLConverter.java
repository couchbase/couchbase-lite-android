//
// CBLConverter.java
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

import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.ClassUtils;


final class CBLConverter {
    static Number asNumber(Object value) {
        // special handling for Boolean
        if (value instanceof Boolean) {
            return ((Boolean) value) ? Integer.valueOf(1) : Integer.valueOf(0);
        }
        else { return ClassUtils.cast(value, Number.class); }
    }

    static boolean asBoolean(Object value) {
        if (value == null) { return false; }
        else if (value instanceof Boolean) { return ((Boolean) value).booleanValue(); }
        else if (value instanceof Number) { return ((Number) value).intValue() != 0; }
        else { return true; }
    }

    static int asInteger(MValue val, MCollection container) {
        if (val.getValue() != null) { return (int) val.getValue().asInt(); }
        else {
            final Number num = asNumber(val.asNative(container));
            return num != null ? num.intValue() : 0;
        }
    }

    static long asLong(MValue val, MCollection container) {
        if (val.getValue() != null) { return val.getValue().asInt(); }
        else {
            final Number num = asNumber(val.asNative(container));
            return num != null ? num.longValue() : 0L;
        }
    }

    static float asFloat(MValue val, MCollection container) {
        if (val.getValue() != null) { return val.getValue().asFloat(); }
        else {
            final Number num = asNumber(val.asNative(container));
            return num != null ? num.floatValue() : 0L;
        }
    }

    static double asDouble(MValue val, MCollection container) {
        if (val.getValue() != null) { return val.getValue().asDouble(); }
        else {
            final Number num = asNumber(val.asNative(container));
            return num != null ? num.doubleValue() : 0L;
        }
    }
}
