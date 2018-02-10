//
// DictionaryInterface.java
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
import java.util.Map;

/**
 * Note: DictionaryInterface is an internal interface. This should not be public.
 */
interface DictionaryInterface {
    int count();

    // Return a COPY of all keys
    List<String> getKeys();

    // Array, Blob, Boolean, Dictionary, Number, String
    Object getValue(String key);

    String getString(String key);

    Number getNumber(String key);

    int getInt(String key);

    long getLong(String key);

    float getFloat(String key);

    double getDouble(String key);

    boolean getBoolean(String key);

    Blob getBlob(String key);

    Date getDate(String key);

    ArrayInterface getArray(String key);

    DictionaryInterface getDictionary(String key);

    Map<String, Object> toMap();

    boolean contains(String key);
}
