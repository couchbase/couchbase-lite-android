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
    private Map<String, Object> map;

    //---------------------------------------------
    // Builder
    //---------------------------------------------

    /**
     * The builder for the Parameters.
     */
    public final static class Builder {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        Parameters params;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        /**
         * Initializes the Parameters's builder.
         */
        public Builder() {
            params = new Parameters();
        }

        /**
         * Initializes the Parameters's builder with the given parameters.
         *
         * @param parameters The parameters.
         */
        public Builder(Parameters parameters) {
            params = parameters != null ? parameters.copy() : new Parameters();
        }

        //---------------------------------------------
        // Setters
        //---------------------------------------------

        /**
         * Set a value to the query parameter referenced by the given name. A query parameter
         * is defined by using the Expression's parameter(String name) function.
         *
         * @param name  The parameter name.
         * @param value The value.
         * @return The self object.
         */
        public Builder setValue(String name, Object value) {
            params.map.put(name, value);
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
        public Builder setString(String name, String value) {
            params.map.put(name, value);
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
        public Builder setNumber(String name, Number value) {
            params.map.put(name, value);
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
        public Builder setInt(String name, int value) {
            params.map.put(name, value);
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
        public Builder setLong(String name, long value) {
            params.map.put(name, value);
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
        public Builder setFloat(String name, float value) {
            params.map.put(name, value);
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
        public Builder setDouble(String name, double value) {
            params.map.put(name, value);
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
        public Builder setBoolean(String name, boolean value) {
            params.map.put(name, value);
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
        public Builder setDate(String name, Date value) {
            params.map.put(name, value);
            return this;
        }

        //---------------------------------------------
        // public API
        //---------------------------------------------

        /**
         * Build a Parameters object with the current parameters.
         *
         * @return The Parameters object.
         */
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
