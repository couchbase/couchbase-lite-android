package com.couchbase.lite;

import java.util.Date;
import java.util.List;
import java.util.Map;

/* package */ interface ReadOnlyDictionaryInterface extends Iterable<String> {
    int count();

    List<String> getKeys();

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

    ReadOnlyArrayInterface getArray(String key);

    ReadOnlyDictionaryInterface getDictionary(String key);

    Map<String, Object> toMap();

    boolean contains(String key);
}
