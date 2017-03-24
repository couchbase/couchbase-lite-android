package com.couchbase.lite;

// TODO: DB005
public interface PropertyChangeEvent {
    Property getSource();

    void setSource(Property property);

    Property getValue();

    void setValue(Property property);

    Property getOldValue();

    void setOldValue(Property property);
}
