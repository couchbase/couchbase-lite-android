package com.couchbase.lite;

public interface Property {
    String getName();

    Object getValue();

    void setValue(Object value);

    boolean exists();

    // TODO: DB004
    void addChangeListener(PropertyChangeListener listener);

    // TODO: DB004
    void removeChangeListener(PropertyChangeListener listener);
}
