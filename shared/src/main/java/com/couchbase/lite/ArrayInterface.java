package com.couchbase.lite;

import java.util.Date;
import java.util.List;

interface ArrayInterface extends ReadOnlyArrayInterface {
    ArrayInterface set(List<Object> list);

    // set
    ArrayInterface setObject(int index, Object value);

    ArrayInterface setString(int index, String value);

    ArrayInterface setNumber(int index, Number value);

    ArrayInterface setInt(int index, int value);

    ArrayInterface setLong(int index, long value);

    ArrayInterface setFloat(int index, float value);

    ArrayInterface setDouble(int index, double value);

    ArrayInterface setBoolean(int index, boolean value);

    ArrayInterface setBlob(int index, Blob value);

    ArrayInterface setDate(int index, Date value);

    ArrayInterface setArray(int index, Array value);

    ArrayInterface setDictionary(int index, Dictionary value);

    // add
    ArrayInterface addObject(Object value);

    ArrayInterface addString(String value);

    ArrayInterface addNumber(Number value);

    ArrayInterface addInt(int value);

    ArrayInterface addLong(long value);

    ArrayInterface addFloat(float value);

    ArrayInterface addDouble(double value);

    ArrayInterface addBoolean(boolean value);

    ArrayInterface addBlob(Blob value);

    ArrayInterface addDate(Date value);

    ArrayInterface addArray(Array value);

    ArrayInterface addDictionary(Dictionary value);

    // insert
    ArrayInterface insertObject(int index, Object value);

    ArrayInterface insertString(int index, String value);

    ArrayInterface insertNumber(int index, Number value);

    ArrayInterface insertInt(int index, int value);

    ArrayInterface insertLong(int index, long value);

    ArrayInterface insertFloat(int index, float value);

    ArrayInterface insertDouble(int index, double value);

    ArrayInterface insertBoolean(int index, boolean value);

    ArrayInterface insertBlob(int index, Blob value);

    ArrayInterface insertDate(int index, Date value);

    ArrayInterface insertArray(int index, Array value);

    ArrayInterface insertDictionary(int index, Dictionary value);

    ArrayInterface remove(int value);

    ArrayInterface getArray(int index);

    DictionaryInterface getDictionary(int index);
}
