package com.couchbase.lite;

import java.util.Date;
import java.util.Map;

/* package */ interface ReadOnlyDictionaryInterface {
    long count();

    Object getObject(String key);

    String getString(String key);

    Number getNumber(String key);

    int getInt(String key);

    long getLong(String key);

    float getFloat(String key);

    double getDouble(String key);

    boolean getBoolean(String key);

    Blob getBlob(String key);

    Date getDate(String key);

    ReadOnlyArray getArray(String key);

    ReadOnlyDictionary getDictionary(String key);

    Map<String, Object> toMap();

    boolean contains(String key);
}
