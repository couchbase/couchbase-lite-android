package com.couchbase.lite;

import com.couchbase.lite.internal.support.JsonUtils;

import org.json.JSONException;

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

    public void set(String name, Object value) {
        params.put(name, value);
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
