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

import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import static com.couchbase.lite.internal.utils.ClassUtils.cast;

final class CBLConverter {
    static Number getNumber(Object value) {
        // special handling for Boolean
        if (value != null && value instanceof Boolean)
            return value == Boolean.TRUE ? Integer.valueOf(1) : Integer.valueOf(0);
        else
            return cast(value, Number.class);
    }

    static int asInteger(MValue val, MCollection container) {
        if (val.getValue() != null)
            return (int) val.getValue().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.intValue() : 0;
        }
    }

    static long asLong(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asInt();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.longValue() : 0L;
        }
    }

    static float asFloat(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asFloat();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.floatValue() : 0L;
        }
    }

    static double asDouble(MValue val, MCollection container) {
        if (val.getValue() != null)
            return val.getValue().asDouble();
        else {
            Number num = getNumber(val.asNative(container));
            return num != null ? num.doubleValue() : 0L;
        }
    }
}
