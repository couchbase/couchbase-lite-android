package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;

class Properties {

    // TODO: DB005 - sharedKeys
    FLDict root;
    Map<String, Object> properties;
    // TODO: DB005 - changesKeys;
    boolean hasChanges;

    //---------------------------------------------
    // public API methods
    //---------------------------------------------

    public Map<String, Object> getProperties() {
        if (properties == null)
            properties = getSavedProperties();
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        if (properties != null)
            this.properties = new HashMap<>(properties);
        else
            this.properties = new HashMap<>();

        // TODO: DB005 - Special case handling for Blob

        markChanges();
    }

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

    public Object get(String key) {
        return getObject(key);
    }

    public String getString(String key) {
        if (properties != null)
            return cast(properties.get(key), String.class);
        else {
            FLValue flvalue = getValueFromRoot(key);
            return flvalue == null ? null : flvalue.asString();
        }
    }

    public int getInt(String key) {
        if (properties != null) {
            Number obj = cast(properties.get(key), Number.class);
            return obj == null ? 0 : obj.intValue();
        } else {
            FLValue flvalue = getValueFromRoot(key);
            return flvalue == null ? 0 : flvalue.asInt();
        }
    }

    public float getFloat(String key) {
        if (properties != null) {
            Number obj = cast(properties.get(key), Number.class);
            return obj == null ? 0.0F : obj.floatValue();
        } else {
            FLValue flvalue = getValueFromRoot(key);
            return flvalue == null ? 0.0F : flvalue.asFloat();
        }
    }

    public double getDouble(String key) {
        if (properties != null) {
            Number obj = cast(properties.get(key), Number.class);
            return obj == null ? 0.0 : obj.doubleValue();
        } else {
            FLValue flvalue = getValueFromRoot(key);
            return flvalue == null ? 0.0 : flvalue.asDouble();
        }
    }

    public boolean getBoolean(String key) {
        if (properties != null) {
            Object val = properties.get(key);
            if (val == null) return false;
            Boolean obj = cast(val, Boolean.class);
            // NOTE: to be consistent with other platform, return true if failed to cast.
            return obj == null ? true : obj.booleanValue();
        } else {
            FLValue flvalue = getValueFromRoot(key);
            return flvalue == null ? false : flvalue.asBool();
        }
    }

    public Blob getBlob(String key) {
        // TODO: DB005
        return null;
    }

    public Date getDate(String key) {
        return DateUtils.fromJson(getString(key));
    }

    public List<Object> getArray(String key) {
        if (properties != null) {
            return cast(properties.get(key), List.class);
        } else {
            FLValue flvalue = getValueFromRoot(key);
            // NOTE: need to check type, otherwise return empty array instead of null
            return flvalue == null || flvalue.getType() != kFLArray ? null : flvalue.asArray();
        }
    }

    public SubDocument getSubDocument(String key) {
        // TODO: DB005
        return null;
    }

    public Document getDocument(String key) {
        // TODO: DB005
        return null;
    }

    public List<Document> getDocuments(String key) {
        // TODO: DB005
        return null;
    }

    public Property getProperty(String key) {
        // TODO: DB005
        return null;
    }

    public Properties remove(String key) {
        mutateProperties();
        properties.remove(key);
        markChanges();
        return this;
    }

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

    private static <T> T cast(Object obj, Class<T> clazz) {
        if (obj != null && !clazz.isInstance(obj))
            return null;
        return (T) obj;
    }

    private FLValue getValueFromRoot(String key) {
        return root == null ? null : root.get(key);
    }
}
