package com.couchbase.lite;

import com.couchbase.lite.internal.support.JsonUtils;

import org.json.JSONException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Parameters {
    private final static String TAG = Log.QUERY;

    private Map<String, Object> params;

    Parameters() {
        this.params = new HashMap<>();
    }

    private Parameters(Map<String, Object> params) {
        this.params = new HashMap<>(params);
    }

    public Parameters setObject(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public Parameters setString(String name, String value) {
        params.put(name, value);
        return this;
    }

    public Parameters setNumber(String name, Number value) {
        params.put(name, value);
        return this;
    }

    public Parameters setInt(String name, int value) {
        params.put(name, value);
        return this;
    }

    public Parameters setLong(String name, long value) {
        params.put(name, value);
        return this;
    }

    public Parameters setFloat(String name, float value) {
        params.put(name, value);
        return this;
    }

    public Parameters setDouble(String name, double value) {
        params.put(name, value);
        return this;
    }

    public Parameters setBoolean(String name, boolean value) {
        params.put(name, value);
        return this;
    }

    public Parameters setDate(String name, Date value) {
        params.put(name, value);
        return this;
    }

    Parameters copy() {
        return new Parameters(params);
    }

    String encodeAsJSON() {
        try {
            return JsonUtils.toJson(params).toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error when encoding the query parameters as a json string", e);
        }
        return null;
    }
}
