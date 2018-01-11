package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;

import org.json.JSONException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class Parameters {

    private final static String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Map<String, Object> map;

    //---------------------------------------------
    // Builder
    //---------------------------------------------
    public final static class Builder {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        Parameters params;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        public Builder() {
            params = new Parameters();
        }

        public Builder(Parameters parameters) {
            params = parameters.copy();
        }

        //---------------------------------------------
        // Setters
        //---------------------------------------------
        public Builder setValue(String name, Object value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setString(String name, String value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setNumber(String name, Number value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setInt(String name, int value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setLong(String name, long value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setFloat(String name, float value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setDouble(String name, double value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setBoolean(String name, boolean value) {
            params.map.put(name, value);
            return this;
        }

        public Builder setDate(String name, Date value) {
            params.map.put(name, value);
            return this;
        }

        //---------------------------------------------
        // public API
        //---------------------------------------------
        public Parameters build() {
            return params.copy();
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private Parameters() {
        map = new HashMap<>();
    }

    private Parameters(Map<String, Object> map) {
        this.map = new HashMap<>(map);
    }

    //---------------------------------------------
    // public API
    //---------------------------------------------
    public Object getValue(String name) {
        return map.get(name);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    Parameters copy() {
        return new Parameters(map);
    }

    String encodeAsJSON() {
        try {
            return JsonUtils.toJson(map).toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error when encoding the query parameters as a json string", e);
        }
        return null;
    }
}
