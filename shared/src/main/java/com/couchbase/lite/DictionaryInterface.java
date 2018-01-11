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
