package com.couchbase.lite;

interface DocumentChangeEvent {
    Document getSource();

    void setSource(Document source);

    Document getValue();

    void setValue(Document value);

    Document getOldValue();

    void setOldValue(Document oldValue);
}
