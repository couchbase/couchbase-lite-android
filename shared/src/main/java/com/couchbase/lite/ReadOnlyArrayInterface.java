package com.couchbase.lite;

import java.util.Date;
import java.util.List;

/* package */ interface ReadOnlyArrayInterface {
    int count();

    Object getObject(int index);

    String getString(int index);

    Number getNumber(int index);

    int getInt(int index);

    long getLong(int index);

    float getFloat(int index);

    double getDouble(int index);

    boolean getBoolean(int index);

    Blob getBlob(int index);

    Date getDate(int index);

    ReadOnlyArrayInterface getArray(int index);

    ReadOnlyDictionaryInterface getDictionary(int index);

    List<Object> toList();
}
