package com.couchbase.lite;

import java.util.Date;
import java.util.Map;

interface DictionaryInterface extends ReadOnlyDictionaryInterface {
    DictionaryInterface setObject(String key, Object value);

    DictionaryInterface setString(String key, String value);

    DictionaryInterface setNumber(String key, Number value);

    DictionaryInterface setInt(String key, int value);

    DictionaryInterface setLong(String key, long value);

    DictionaryInterface setFloat(String key, float value);

    DictionaryInterface setDouble(String key, double value);

    DictionaryInterface setBoolean(String key, boolean value);

    DictionaryInterface setBlob(String key, Blob value);

    DictionaryInterface setDate(String key, Date value);

    DictionaryInterface setArray(String key, Array value);

    DictionaryInterface setDictionary(String key, Dictionary value);

    DictionaryInterface remove(String key);

    DictionaryInterface set(Map<String, Object> dictionary);

    ArrayInterface getArray(String key);

    DictionaryInterface getDictionary(String key);
}
