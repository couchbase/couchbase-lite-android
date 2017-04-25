/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.internal.support.ClassUtils;
import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLDictIterator;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLArray;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;

/**
 * Properties defines a JSON-compatible object, much like a Map (Dictionary) but with
 * type-safe accessors. It is implemented by classes {@code Document} and {@code SubDocument}.
 */
public abstract class Properties {
    SharedKeys sharedKeys;
    FLDict root;
    Map<String, Object> properties;
    // TODO: DB00x - changesKeys;
    boolean hasChanges;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Properties(SharedKeys sharedKeys) {
        this.sharedKeys = sharedKeys;
    }

    //---------------------------------------------
    // public API methods
    //---------------------------------------------

    /**
     * Returns all of the properties contained in this object.
     *
     * @return the all of the properties
     */
    public Map<String, Object> getProperties() {
        if (properties == null)
            properties = getSavedProperties();
        return properties;
    }

    /**
     * Set the properties in this object.
     *
     * @param properties th properties
     */
    public void setProperties(Map<String, Object> properties) {
        Map<String, Object> props = (properties != null) ?
                new HashMap<>(properties) :
                new HashMap<String, Object>();

        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Object converted = convert((Map<String, Object>) entry.getValue());
                    if (converted != null)
                        props.put(entry.getKey(), converted);
                }
            }
        }

        this.properties = props;
        markChanges();
    }

    /**
     * Sets a property value by key.
     * Allowed value types are null, Boolean, Integer, Long, Float, Double, String, List, Map, Date,
     * SubDocument, and Blob. List (Array) and Map (Dictionary) must contain only the above types.
     * Setting a null value will remove the property.
     * <p>
     * Note:
     * A Date object will be converted to an ISO-8601 format string.
     * When setting a SubDocument, the SubDocument will be set by reference. However,
     * if the SubDocument has already been set to another key either on the same or different
     * Document, the value of the SubDocument will be copied instead.
     *
     * @return this object
     */
    public Properties set(String key, Object value) {
        if (key == null) throw new IllegalArgumentException("null key is not allowed");

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

    /**
     * Gets an property's value as an object. Returns types null, Integer, Long, Float, Double,
     * String, List, Map, and Blob, based on the underlying data type; or null if the property
     * doesn't exist.
     *
     * @param key The key to access the value for.
     * @return null if there is no such key.
     */
    public Object get(String key) {
        return getObject(key);
    }

    /**
     * Gets a property's value as a string.
     * returns null if the property doesn't exist, or its value is not a string.
     *
     * @param key The key to access the value for.
     */
    public String getString(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null)
            return ClassUtils.cast(val, String.class);
        else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? null : flvalue.asString();
        }
    }

    /**
     * Gets a property's value as an integer.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key The key to access the value for.
     * @return 0 if there is no such key or if it is not a Number.
     */
    public int getInt(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null) {
            Number obj = ClassUtils.cast(val, Number.class);
            return obj == null ? 0 : obj.intValue();
        } else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? 0 : flvalue.asInt();
        }
    }

    /**
     * Gets a property's value as a float.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key The key to access the value for.
     * @return 0 if there is no such key or if it is not a Number.
     */
    public float getFloat(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null) {
            Number obj = ClassUtils.cast(val, Number.class);
            return obj == null ? 0.0F : obj.floatValue();
        } else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? 0.0F : flvalue.asFloat();
        }
    }

    /**
     * Gets a property's value as a double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key The key to access the value for.
     * @return 0 if there is no such key or if it is not a Number.
     */
    public double getDouble(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null) {
            Number obj = ClassUtils.cast(val, Number.class);
            return obj == null ? 0.0 : obj.doubleValue();
        } else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? 0.0 : flvalue.asDouble();
        }
    }

    /**
     * Gets a property's value as a boolean.
     * Returns true if the value exists, and is either `true` or a nonzero number.
     *
     * @param key The key to access the value for.
     */
    public boolean getBoolean(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null) {
            Boolean obj = ClassUtils.cast(val, Boolean.class);
            // NOTE: to be consistent with other platform, return true if failed to cast.
            return obj == null ? true : obj.booleanValue();
        } else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? false : flvalue.asBool();
        }
    }

    /**
     * Gets a property's value as a blob object.
     * Returns null if the property doesn't exist, or its value is not a blob.
     *
     * @param key The key to access the value for.
     */
    public Blob getBlob(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null)
            return ClassUtils.cast(val, Blob.class);
        else {
            FLValue flvalue = fleeceValue(key);
            return flvalue == null ? null : (Blob) fleeceValueToObject(flvalue);
        }
    }

    /**
     * Gets a property's value as an Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parse-able as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key The key to access the value for.
     */
    public Date getDate(String key) {
        return DateUtils.fromJson(getString(key));
    }

    /**
     * Get a property's value as an array object.
     * Returns null if the property doesn't exist, or its value is not an array.
     *
     * @param key The key to access the value for.
     */
    public List<Object> getArray(String key) {
        Object val;
        if (properties != null && (val = properties.get(key)) != null) {
            return ClassUtils.cast(val, List.class);
        } else {
            FLValue flvalue = fleeceValue(key);
            // NOTE: need to check type, otherwise return empty array instead of null
            return flvalue == null || flvalue.getType() != kFLArray ? null : flvalue.asArray();
        }
    }

    /**
     * Get a property's value as a Subdocument, which is a mapping object of a Dictionary
     * value to provide property type accessors.
     * Returns null if the property doesn't exists, or its value is not a Dictionary.
     *
     * @param key The key to access the value for.
     */
    public SubDocument getSubDocument(String key) {
        // TODO: DB00x
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * @param key The key to access the value for.
     */
    public Document getDocument(String key) {
        // TODO: DB00x
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * @param key The key to access the value for.
     */
    public List<Document> getDocuments(String key) {
        // TODO: DB00x
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * Removes a key from this object's data if it exists.
     *
     * @param key The key to remove.
     * @return this object
     */
    public Properties remove(String key) {
        mutateProperties();
        properties.remove(key);
        markChanges();
        return this;
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than calling property(key):, because it does not have to allocate
     * an object for the property value.
     *
     * @param key The key to access the value for.
     * @return true if exists, false otherwise
     */
    public boolean contains(String key) {
        if (properties != null)
            return properties.containsKey(key);
        else
            return fleeceValue(key) == null;
    }


    /**
     * Reverts unsaved changes made to the properties.
     */
    public void revert() {
        // TODO: DB00x - part of changeskeys
        hasChanges = false;
    }

    //---------------------------------------------
    // Implementation of Iterable
    //---------------------------------------------

    /**
     * Implementing for Iterable.
     * Currently iterator() returns keys. Not Key,Value pair.
     *
     * @return the iterator of type {@code String}.
     */
    public Iterator<String> iterator() {
        if (properties != null && hasChanges) {
            return properties.keySet().iterator();
        } else {
            return root == null ? null : root.iterator(sharedKeys);
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
        sharedKeys.useDocumentRoot(this.root);
    }

    abstract Blob blobWithProperties(Map<String, Object> dict);

    void useNewRoot() {
        if (properties == null)
            return;
        Map<String, Object> nuProps = new HashMap<>();
        //TODO: SubDocument
        properties = nuProps;
    }

    Map<String, Object> getSavedProperties() {
        if (properties != null && !hasChanges)
            return properties;
        else
            return root == null ? null : fleeceRootToDictionary(root);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private Object getObject(String key) {
        Object obj = null;
        if (properties != null && (obj = properties.get(key)) != null)
            return obj;
        else
            return root == null ? null : fleeceValueToObject(fleeceValue(key));
    }

    private void mutateProperties() {
        if (properties == null) {
            properties = root == null ? null : fleeceRootToDictionary(root);
            if (properties == null)
                properties = new HashMap<>();
        }
    }

    private Object convert(Map<String, Object> dict) {
        if (isBlobMap(dict))
            return blobWithProperties(dict);

        for (Map.Entry<String, Object> entry : dict.entrySet()) {
            if (isBlobMap(entry.getValue()))
                dict.put(entry.getKey(), blobWithProperties((Map<String, Object>) entry.getValue()));
        }

        // no conversion
        return dict;
    }

    private boolean isBlobMap(Object obj) {
        if (obj != null && obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            Object value = map.get("_cbltype");
            if (value != null && value instanceof String && "blob".equals(value))
                return true;
        }
        return false;
    }

    private FLValue fleeceValue(String key) {
        return root == null ? null : SharedKeys.getValue(root, key, sharedKeys);
    }

    // - (id) fleeceValueToObject: (FLValue)value forKey: (NSString*)key
    private Object fleeceValueToObject(FLValue value) {
        if (value == null) return null;
        switch (value.getType()) {
            case kFLDict:
                //Map<String, Object> dict = value.asDict();
                // TODO: for SubDocument
                Map<String, Object> res = (Map<String, Object>) SharedKeys.valueToObject(value, sharedKeys);
                return convert(res);
            case kFLArray:
                // TODO:
            default:
                return SharedKeys.valueToObject(value, sharedKeys);
        }
    }

    // - (NSDictionary*) fleeceRootToDictionary: (FLDict)root
    private Map<String, Object> fleeceRootToDictionary(FLDict root) {
        if (root == null) return null;

        Map<String, Object> dict = new HashMap<>();
        FLDictIterator itr = new FLDictIterator();
        itr.begin(root);
        String key;
        while ((key = SharedKeys.getKey(itr, sharedKeys)) != null) {
            dict.put(key, fleeceValueToObject(itr.getValue()));
            itr.next();
        }
        itr.free();

        return dict;
    }
}
