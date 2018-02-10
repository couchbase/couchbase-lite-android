package com.couchbase.lite;

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

    List<Object> toList();
}
