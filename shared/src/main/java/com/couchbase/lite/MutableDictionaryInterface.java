package com.couchbase.lite;

import java.util.Date;
import java.util.Map;

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
