//
// MutableArrayInterface.java
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
 * Note: MutableArrayInterface is an internal interface. sThis should not be public.
 */
interface MutableArrayInterface extends ArrayInterface {
    @NonNull
    MutableArrayInterface setData(List<Object> data);

    // set

    @NonNull
    MutableArrayInterface setValue(int index, Object value);

    @NonNull
    MutableArrayInterface setString(int index, String value);

    @NonNull
    MutableArrayInterface setNumber(int index, Number value);

    @NonNull
    MutableArrayInterface setInt(int index, int value);

    @NonNull
    MutableArrayInterface setLong(int index, long value);

    @NonNull
    MutableArrayInterface setFloat(int index, float value);

    @NonNull
    MutableArrayInterface setDouble(int index, double value);

    @NonNull
    MutableArrayInterface setBoolean(int index, boolean value);

    @NonNull
    MutableArrayInterface setBlob(int index, Blob value);

    @NonNull
    MutableArrayInterface setDate(int index, Date value);

    @NonNull
    MutableArrayInterface setArray(int index, Array value);

    @NonNull
    MutableArrayInterface setDictionary(int index, Dictionary value);

    // add

    @NonNull
    MutableArrayInterface addValue(Object value);

    @NonNull
    MutableArrayInterface addString(String value);

    @NonNull
    MutableArrayInterface addNumber(Number value);

    @NonNull
    MutableArrayInterface addInt(int value);

    @NonNull
    MutableArrayInterface addLong(long value);

    @NonNull
    MutableArrayInterface addFloat(float value);

    @NonNull
    MutableArrayInterface addDouble(double value);

    @NonNull
    MutableArrayInterface addBoolean(boolean value);

    @NonNull
    MutableArrayInterface addBlob(Blob value);

    @NonNull
    MutableArrayInterface addDate(Date value);

    @NonNull
    MutableArrayInterface addArray(Array value);

    @NonNull
    MutableArrayInterface addDictionary(Dictionary value);

    // insert

    @NonNull
    MutableArrayInterface insertValue(int index, Object value);

    @NonNull
    MutableArrayInterface insertString(int index, String value);

    @NonNull
    MutableArrayInterface insertNumber(int index, Number value);

    @NonNull
    MutableArrayInterface insertInt(int index, int value);

    @NonNull
    MutableArrayInterface insertLong(int index, long value);

    @NonNull
    MutableArrayInterface insertFloat(int index, float value);

    @NonNull
    MutableArrayInterface insertDouble(int index, double value);

    @NonNull
    MutableArrayInterface insertBoolean(int index, boolean value);

    @NonNull
    MutableArrayInterface insertBlob(int index, Blob value);

    @NonNull
    MutableArrayInterface insertDate(int index, Date value);

    @NonNull
    MutableArrayInterface insertArray(int index, Array value);

    @NonNull
    MutableArrayInterface insertDictionary(int index, Dictionary value);

    // remove

    @NonNull
    MutableArrayInterface remove(int value);

    // overridden

    MutableArrayInterface getArray(int index);

    MutableDictionaryInterface getDictionary(int index);
}
