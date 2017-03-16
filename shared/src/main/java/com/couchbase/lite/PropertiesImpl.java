package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class PropertiesImpl implements Properties {

    // TODO: DB004 sharedKeys
    private FLDict root;
    private Map<String, Object> properties;
    // TODO: changesKeys;
    /*package*/ boolean hasChanges;

    @Override
    public Map<String, Object> getProperties() {
        if (!hasChanges) {
            // TODO:
        }
        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {

    }

    @Override
    public Properties set(String key, Object value) {
        setObject(key, value);
        return this;
    }

    @Override
    public Object get(String key) {
        return getObject(key);
    }

    @Override
    public String getString(String key) {
        if (properties != null)
            return (String) properties.get(key);
        else
            return root == null ? null : root.get(key).asString();
    }

//    @Override
//    public Number getNumber(String key) {
//        if (properties != null)
//            return (Number) properties.get(key);
//        else
//            return null; //TODO
//    }

    @Override
    public int getInt(String key) {
        if (properties != null)
            return (int) properties.get(key);
        else
            return root == null ? 0 : root.get(key).asInt();
    }

    @Override
    public double getDouble(String key) {
        if (properties != null)
            return (double) properties.get(key);
        else
            return root == null ? 0.0 : root.get(key).asDouble();
    }

    @Override
    public boolean getBoolean(String key) {
        if (properties != null)
            return (boolean) properties.get(key);
        else
            return root == null ? false : root.get(key).asBool();
    }

    @Override
    public Blob getBlob(String key) {
        // TODO: DB004
        return null;
    }

    @Override
    public Date getDate(String key) {
        // TODO: DB004
        return null;
    }

    @Override
    public List<Object> getArray(String key) {
        if (properties != null)
            return (List<Object>) properties.get(key);
        else
            // TODO
            return null;
    }

    @Override
    public SubDocument getSubDocument(String key) {
        // TODO: DB004
        return null;
    }

    @Override
    public Document getDocument(String key) {
        // TODO: DB004
        return null;
    }

    @Override
    public List<Document> getDocuments(String key) {
        // TODO: DB004
        return null;
    }

    @Override
    public Property getProperty(String key) {
        return null;
    }

    @Override
    public Properties remove(String key) {
        return null;
    }

    @Override
    public boolean contains(String key) {
        return false;
    }

    @Override
    public Iterator iterator() {
        return null;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    void setHasChanges(boolean hasChanges) {
        this.hasChanges = hasChanges;
    }

    void markChanges() {
        if (!hasChanges)
            hasChanges = true;
    }

    void setRoot(FLDict root) {
        this.root = root;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void setObject(String key, Object value) {
        // TODO: Date conversion
        if (hasChanges || (value == null ? getObject(key) != null : !value.equals(getObject(key)))) {
            mutateProperties();
            properties.put(key, value);
            markChanges();
        }
    }

    private Object getObject(String key) {
        if (properties != null)
            return properties.get(key);
        else
            // TODO: FL
            return null;
    }

    private void mutateProperties() {
        if (properties == null) {
            //TODO: fleeceRootToDictionary
            if (properties == null)
                properties = new HashMap<>();
        }
    }
}
