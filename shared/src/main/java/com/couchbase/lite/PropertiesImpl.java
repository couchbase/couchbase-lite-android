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
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLBoolean;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLNumber;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLString;

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
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof String)
                return (String) obj;
            else
                return null;
        } else {
            if (root == null)
                return null;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLString)
                    return flvalue.asString();
                else
                    return null;
            }
        }
    }

    @Override
    public int getInt(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof Number)
                return ((Number) obj).intValue();
            else
                return 0;
        } else {
            if (root == null)
                return 0;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLNumber)
                    return flvalue.asInt();
                else
                    return 0;
            }
        }
    }

    @Override
    public float getFloat(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof Number)
                return ((Number) obj).floatValue();
            else
                return 0.0F;
        } else{
            if (root == null)
                return 0.0F;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLNumber)
                    return flvalue.asFloat();
                else
                    return 0.0F;
            }
        }
    }

    @Override
    public double getDouble(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof Number)
                return ((Number) obj).doubleValue();
            else
                return 0.0;
        } else {
            if (root == null)
                return 0.0;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLNumber)
                    return flvalue.asDouble();
                else
                    return 0.0;
            }
        }
    }

    @Override
    public boolean getBoolean(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof Boolean)
                return ((Boolean) obj).booleanValue();
            else if (obj != null && obj instanceof String)
                return Boolean.valueOf((String) obj);
            else
                return false;
        } else {
            if (root == null)
                return false;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLBoolean)
                    return flvalue.asBool();
                else
                    return false;
            }
        }
    }

    @Override
    public Blob getBlob(String key) {
        // TODO: DB005
        return null;
    }

    @Override
    public Date getDate(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof String)
                return DateUtils.fromJson((String) obj);
            else
                return null;
        } else {
            if (root == null)
                return null;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLString)
                    return DateUtils.fromJson(flvalue.asString());
                else
                    return null;
            }
        }
    }

    @Override
    public List<Object> getArray(String key) {
        if (properties != null) {
            Object obj = properties.get(key);
            if (obj != null && obj instanceof List<?>)
                return (List<Object>) obj;
            else
                return null;
        } else {
            if (root == null)
                return null;
            else {
                FLValue flvalue = root.get(key);
                if (flvalue.getType() == kFLArray)
                    return flvalue.asArray();
                else
                    return null;
            }
        }
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
