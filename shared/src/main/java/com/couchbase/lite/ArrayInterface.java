//
// ArrayInterface.java
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

import android.support.annotation.NonNull;

import java.util.Date;
import java.util.List;


/**
 * Note: ArrayInterface is an internal interface. This should not be public.
 */
interface ArrayInterface {
    int count();

    // Array, Blob, Boolean, Dictionary, Number, String
    Object getValue(int index);

    String getString(int index);

    Number getNumber(int index);

    int getInt(int index);

    long getLong(int index);

    float getFloat(int index);

    double getDouble(int index);

    boolean getBoolean(int index);

    Blob getBlob(int index);

    Date getDate(int index);

    ArrayInterface getArray(int index);

    DictionaryInterface getDictionary(int index);

    @NonNull
    List<Object> toList();
}
