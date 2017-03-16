package com.couchbase.lite;

public interface Property {
    String getName();

    Object getValue();

    void setValue(Object value);

    boolean exists();

    void addChangeListener(PropertyChangeListener listener);

    void removeChangeListener(PropertyChangeListener listener);
}
