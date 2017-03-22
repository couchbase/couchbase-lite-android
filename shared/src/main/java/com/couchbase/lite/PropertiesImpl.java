package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class PropertiesImpl implements Properties {

    // TODO: DB005 - sharedKeys
    FLDict root;
    Map<String, Object> properties;
    // TODO: DB005 - changesKeys;
    boolean hasChanges;

    @Override
    public Map<String, Object> getProperties() {
        if (properties == null)
            properties = getSavedProperties();
        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties != null)
            this.properties = new HashMap<>(properties);
        else
            this.properties = new HashMap<>();

        // TODO: DB005 - Special case handling for Blob

        markChanges();
    }

    @Override
    public Properties set(String key, Object value) {
        // Date
        if (value instanceof Date)
            value = DateUtils.toJson((Date) value);

        if (hasChanges || (value == null ? getObject(key) != null : !value.equals(getObject(key)))) {
            mutateProperties();
            properties.put(key, value);
            markChanges();
        }

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

    @Override
    public int getInt(String key) {
        if (properties != null)
            return (int) properties.get(key);
        else
            return root == null ? 0 : root.get(key).asInt();
    }

    @Override
    public float getFloat(String key) {
        if (properties != null)
            return (float) properties.get(key);
        else
            return root == null ? 0.0F : root.get(key).asFloat();
    }

    @Override
    public double getDouble(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof Number)
                return ((Number) obj).doubleValue();
            else
                return 0.0;
        } else
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
        // TODO: DB005
        return null;
    }

    @Override
    public Date getDate(String key) {
        if (properties != null)
            return DateUtils.fromJson((String) properties.get(key));
        else
            return root == null ? null : DateUtils.fromJson(root.get(key).asString());
    }

    @Override
    public List<Object> getArray(String key) {
        if (properties != null)
            return (List<Object>) properties.get(key);
        else
            return root == null ? null : root.get(key).asArray();
    }

    @Override
    public SubDocument getSubDocument(String key) {
        // TODO: DB005
        return null;
    }

    @Override
    public Document getDocument(String key) {
        // TODO: DB005
        return null;
    }

    @Override
    public List<Document> getDocuments(String key) {
        // TODO: DB005
        return null;
    }

    @Override
    public Property getProperty(String key) {
        // TODO: DB005
        return null;
    }

    @Override
    public Properties remove(String key) {
        mutateProperties();
        properties.remove(key);
        markChanges();
        return this;
    }

    @Override
    public boolean contains(String key) {
        if (properties != null)
            return properties.containsKey(key);
        else
            // TODO: DB005 - Need to update once shared key is implemented.
            return root == null ? false : root.asDict().containsKey(key);
    }

    //---------------------------------------------
    // Implementation of Iterable
    //---------------------------------------------

    /**
     * Implementing for Iterable.
     * Currently iterator() returns keys. Not Key,Value pair.
     *
     * @return Iterator<String>
     */
    @Override
    public Iterator<String> iterator() {
        if (properties != null) {
            return properties.keySet().iterator();
        } else {
            return root == null ? null : root.iterator();
        }
    }

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

    private Object getObject(String key) {
        if (properties != null)
            return properties.get(key);
        else
            return root == null ? null : root.get(key).asObject();
    }

    private void mutateProperties() {
        if (properties == null) {
            properties = root == null ? null : root.asDict();
            if (properties == null)
                properties = new HashMap<>();
        }
    }

    private Map<String, Object> getSavedProperties() {
        if (properties != null && !hasChanges)
            return properties;
        else
            return root == null ? null : root.asDict();
    }
}
