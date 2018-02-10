package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;

import org.json.JSONException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Parameters object used for setting values to the query parameters defined in the query.
 */
public final class Parameters {
    private final static String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private boolean readonly = false;
    private Map<String, Object> map;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Parameters() {
        readonly = false;
        map = new HashMap<>();
    }

    public Parameters(Parameters parameters) {
        readonly = false;
        map = (parameters == null) ? new HashMap<String, Object>() : new HashMap<>(parameters.map);
    }

    //---------------------------------------------
    // public API
    //---------------------------------------------

    /**
     * Set a value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The value.
     * @return The self object.
     */
    public Parameters setValue(String name, Object value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set an String value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The String value.
     * @return The self object.
     */
    public Parameters setString(String name, String value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set an Number value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Number value.
     * @return The self object.
     */
    public Parameters setNumber(String name, Number value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set an int value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The int value.
     * @return The self object.
     */
    public Parameters setInt(String name, int value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set an long value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The long value.
     * @return The self object.
     */
    public Parameters setLong(String name, long value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set a float value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The float value.
     * @return The self object.
     */
    public Parameters setFloat(String name, float value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set a double value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The double value.
     * @return The self object.
     */
    public Parameters setDouble(String name, double value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set a boolean value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The boolean value
     * @return The self object.
     */
    public Parameters setBoolean(String name, boolean value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Set a date value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(_ name: String) function.
     *
     * @param name  The parameter name.
     * @param value The date value.
     * @return The self object.
     */
    public Parameters setDate(String name, Date value) {
        if (readonly)
            throw new IllegalStateException("Parameters is readonly mode.");
        map.put(name, value);
        return this;
    }

    /**
     * Gets a parameter's value.
     *
     * @param name The parameter name.
     * @return The parameter value.
     */
    public Object getValue(String name) {
        return map.get(name);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    Parameters readonlyCopy() {
        Parameters parameters = new Parameters(this);
        parameters.readonly = true;
        return parameters;
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
