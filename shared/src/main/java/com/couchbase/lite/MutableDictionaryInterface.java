//
// MutableDictionaryInterface.java
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
import java.util.Map;

/**
 * Note: MutableDictionaryInterface is an internal interface. This should not be public.
 */
interface MutableDictionaryInterface extends DictionaryInterface {
    // Set JSON or platform dictionary as a content.
    MutableDictionaryInterface setData(Map<String, Object> data);

    // set
    MutableDictionaryInterface setValue(String key, Object value);

    MutableDictionaryInterface setString(String key, String value);

    MutableDictionaryInterface setNumber(String key, Number value);

    MutableDictionaryInterface setInt(String key, int value);

    MutableDictionaryInterface setLong(String key, long value);

    MutableDictionaryInterface setFloat(String key, float value);

    MutableDictionaryInterface setDouble(String key, double value);

    MutableDictionaryInterface setBoolean(String key, boolean value);

    MutableDictionaryInterface setBlob(String key, Blob value);

    MutableDictionaryInterface setDate(String key, Date value);

    MutableDictionaryInterface setArray(String key, Array value);

    MutableDictionaryInterface setDictionary(String key, Dictionary value);

    // remove

    MutableDictionaryInterface remove(String key);

    // overridden

    MutableArrayInterface getArray(String key);

    MutableDictionaryInterface getDictionary(String key);
}
