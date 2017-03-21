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

    int getInt(String key);

    float getFloat(String key);

    double getDouble(String key);

    boolean getBoolean(String key);

    Blob getBlob(String key);  // TODO: DB005

    Date getDate(String key);

    List<Object> getArray(String key); // NOTE: Should it be List<Object> getList(String key)??

    SubDocument getSubDocument(String key); // TODO: DB005 SubDocumentModel

    // Relationships:
    Document getDocument(String key);

    List<Document> getDocuments(String key); // TODO: DB00x DocumentModel

    // Get a complex property object:
    Property getProperty(String key); // TODO: DB00x PropertyModel

    // Remove:
    Properties remove(String key);

    // Existence:
    boolean contains(String key);
}
