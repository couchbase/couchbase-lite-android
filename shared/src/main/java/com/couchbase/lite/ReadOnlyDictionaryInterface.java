package com.couchbase.lite;

import java.util.Date;
import java.util.Map;

/* package */ interface ReadOnlyDictionaryInterface {
    int count();

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

    /*
    final static ReadOnlyDictionaryInterface EMPTY = new ReadOnlyDictionaryInterface() {

        @Override
        public Object getObject(String key) {
            return null;
        }

        @Override
        public String getString(String key) {
            return null;
        }

        @Override
        public Number getNumber(String key) {
            return null;
        }

        @Override
        public int getInt(String key) {
            return DataUtils.DEFAULT_INT;
        }

        @Override
        public long getLong(String key) {
            return DataUtils.DEFAULT_LONG;
        }

        @Override
        public float getFloat(String key) {
            return DataUtils.DEFAULT_FLOAT;
        }

        @Override
        public double getDouble(String key) {
            return DataUtils.DEFAULT_DOUBLE;
        }

        @Override
        public boolean getBoolean(String key) {
            return DataUtils.DEFAULT_BOOLEAN;
        }

        @Override
        public Date getDate(String key) {
            return null;
        }

        @Override
        public Blob getBlob(String key) {
            return null;
        }

        @Override
        public ReadOnlyArray getArray(String key) {
            return null;
        }

        @Override
        public ReadOnlyDictionary getDictionary(String key) {
            return null;
        }

        @Override
        public boolean contains(String key) {
            return false;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public Map<String, Object> toMap() {
            return Collections.emptyMap();
        }
    };
    */
}
