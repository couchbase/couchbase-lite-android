package com.couchbase.lite;

import java.util.List;

/*package*/ interface ArrayInterface extends ReadOnlyArrayInterface {
    ArrayInterface set(List<Object> list);

    ArrayInterface set(int index, Object value);

    ArrayInterface add(Object value);

    ArrayInterface insert(int index, Object value);

    ArrayInterface remove(int value);

    Array getArray(int index);

    Dictionary getDictionary(int index);
}
