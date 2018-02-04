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

import java.util.Date;
import java.util.List;

/**
 * Note: MutableArrayInterface is an internal interface. sThis should not be public.
 */
interface MutableArrayInterface extends ArrayInterface {
    MutableArrayInterface setData(List<Object> data);

    // set
    MutableArrayInterface setValue(int index, Object value);

    MutableArrayInterface setString(int index, String value);

    MutableArrayInterface setNumber(int index, Number value);

    MutableArrayInterface setInt(int index, int value);

    MutableArrayInterface setLong(int index, long value);

    MutableArrayInterface setFloat(int index, float value);

    MutableArrayInterface setDouble(int index, double value);

    MutableArrayInterface setBoolean(int index, boolean value);

    MutableArrayInterface setBlob(int index, Blob value);

    MutableArrayInterface setDate(int index, Date value);

    MutableArrayInterface setArray(int index, Array value);

    MutableArrayInterface setDictionary(int index, Dictionary value);

    // add
    MutableArrayInterface addValue(Object value);

    MutableArrayInterface addString(String value);

    MutableArrayInterface addNumber(Number value);

    MutableArrayInterface addInt(int value);

    MutableArrayInterface addLong(long value);

    MutableArrayInterface addFloat(float value);

    MutableArrayInterface addDouble(double value);

    MutableArrayInterface addBoolean(boolean value);

    MutableArrayInterface addBlob(Blob value);

    MutableArrayInterface addDate(Date value);

    MutableArrayInterface addArray(Array value);

    MutableArrayInterface addDictionary(Dictionary value);

    // insert
    MutableArrayInterface insertValue(int index, Object value);

    MutableArrayInterface insertString(int index, String value);

    MutableArrayInterface insertNumber(int index, Number value);

    MutableArrayInterface insertInt(int index, int value);

    MutableArrayInterface insertLong(int index, long value);

    MutableArrayInterface insertFloat(int index, float value);

    MutableArrayInterface insertDouble(int index, double value);

    MutableArrayInterface insertBoolean(int index, boolean value);

    MutableArrayInterface insertBlob(int index, Blob value);

    MutableArrayInterface insertDate(int index, Date value);

    MutableArrayInterface insertArray(int index, Array value);

    MutableArrayInterface insertDictionary(int index, Dictionary value);

    // remove

    MutableArrayInterface remove(int value);

    // overridden

    MutableArrayInterface getArray(int index);

    MutableDictionaryInterface getDictionary(int index);
}
