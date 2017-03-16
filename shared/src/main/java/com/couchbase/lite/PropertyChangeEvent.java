package com.couchbase.lite;

public interface PropertyChangeEvent {
    Property getSource();

    void setSource(Property property);

    Property getValue();

    void setValue(Property property);

    Property getOldValue();

    void setOldValue(Property property);
}
