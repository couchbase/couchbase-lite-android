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

    ReadOnlyArray getArray(int index);

    ReadOnlyDictionary getDictionary(int index);

    List<Object> toList();

    /*
    final static ReadOnlyArrayInterface EMPTY = new ReadOnlyArrayInterface() {
        @Override
        public Object getObject(int index) {
            return null;
        }

        @Override
        public String getString(int index) {
            return null;
        }

        @Override
        public Number getNumber(int index) {
            return null;
        }

        @Override
        public int getInt(int index) {
            return DataUtils.DEFAULT_INT;
        }

        @Override
        public long getLong(int index) {
            return DataUtils.DEFAULT_LONG;
        }

        @Override
        public float getFloat(int index) {
            return DataUtils.DEFAULT_FLOAT;
        }

        @Override
        public double getDouble(int index) {
            return DataUtils.DEFAULT_DOUBLE;
        }

        @Override
        public boolean getBoolean(int index) {
            return DataUtils.DEFAULT_BOOLEAN;
        }

        @Override
        public Date getDate(int index) {
            return null;
        }

        @Override
        public Blob getBlob(int index) {
            return null;
        }

        @Override
        public ReadOnlyArray getArray(int index) {
            return null;
        }

        @Override
        public ReadOnlyDictionary getDictionary(int index) {
            return null;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public List<Object> toList() {
            return Collections.emptyList();
        }
    };
    */
}
