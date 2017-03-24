package com.couchbase.lite;

public interface Property {
    String getName();

    Object getValue();

    void setValue(Object value);

    boolean exists();

    // TODO: DB005
    void addChangeListener(PropertyChangeListener listener);

    // TODO: DB005
    void removeChangeListener(PropertyChangeListener listener);
}
