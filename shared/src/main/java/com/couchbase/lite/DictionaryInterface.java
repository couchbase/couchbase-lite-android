package com.couchbase.lite;

import java.util.Map;

/* package */ interface DictionaryInterface extends ReadOnlyDictionaryInterface {
    DictionaryInterface set(String key, Object value);

    DictionaryInterface remove(String key);

    DictionaryInterface set(Map<String, Object> dictionary);

    ArrayInterface getArray(String key);

    DictionaryInterface getDictionary(String key);
}
