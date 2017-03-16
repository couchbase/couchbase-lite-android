package com.couchbase.lite;

import java.util.Date;
import java.util.List;
import java.util.Map;

interface Properties extends Iterable {
    // Document properties:

    Map<String, Object> getProperties();

    void setProperties(Map<String, Object> properties);

    // Getter and Setter accessors:

    Properties set(String key, Object value); // return `this`
    Object get(String key); // String, Number, Boolean, SubDocument, Blob, Array(List)
    String getString(String key);
    //Number getNumber(String key);
    int getInt(String key);
    double getDouble(String key);
    boolean getBoolean(String key);
    // TODO: DB004
    Blob getBlob(String key);
    Date getDate(String key);
    List<Object> getArray(String key); // NOTE: Should it be List<Object> getList(String key)??
    SubDocument getSubDocument(String key);
    // TODO: DB004 SubdocumentModel

    // Relationships:
    Document getDocument(String key);
    List<Document> getDocuments(String key);
    // TODO: DB00x DocumentModel

    // Get a complex property object:
    Property getProperty(String key);
    // TODO: DB00x PropertyModel

    // Remove:
    Properties remove(String key);

    // Existence:
    boolean contains(String key);
}
