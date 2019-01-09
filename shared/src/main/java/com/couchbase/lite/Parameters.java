//
// Parameters.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.Encoder;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A Parameters object used for setting values to the query parameters defined in the query.
 */
public final class Parameters {

    //---------------------------------------------
    // Constants
    //---------------------------------------------

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
     * @param name The parameter name.
     * @param value The String value.
     * @return The self object.
     */
    public Parameters setString(String name, String value) {
        return setValue(name, value);
    }

    /**
     * Set an Number value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The Number value.
     * @return The self object.
     */
    public Parameters setNumber(String name, Number value) {
        return setValue(name, value);
    }

    /**
     * Set an int value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The int value.
     * @return The self object.
     */
    public Parameters setInt(String name, int value) {
        return setValue(name, value);
    }

    /**
     * Set an long value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The long value.
     * @return The self object.
     */
    public Parameters setLong(String name, long value) {
        return setValue(name, value);
    }

    /**
     * Set a float value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The float value.
     * @return The self object.
     */
    public Parameters setFloat(String name, float value) {
        return setValue(name, value);
    }

    /**
     * Set a double value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The double value.
     * @return The self object.
     */
    public Parameters setDouble(String name, double value) {
        return setValue(name, value);
    }

    /**
     * Set a boolean value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The boolean value.
     * @return The self object.
     */
    public Parameters setBoolean(String name, boolean value) {
        return setValue(name, value);
    }

    /**
     * Set a date value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The date value.
     * @return The self object.
     */
    public Parameters setDate(String name, Date value) {
        return setValue(name, value);
    }

    /**
     * Set the Blob value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The Blob value.
     * @return The self object.
     */
    public Parameters setBlob(String name, Blob value) {
        return setValue(name, value);
    }

    /**
     * Set the Dictionary value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The Dictionary value.
     * @return The self object.
     */
    public Parameters setDictionary(String name, Dictionary value) {
        return setValue(name, value);
    }

    /**
     * Set the Array value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name The parameter name.
     * @param value The Array value.
     * @return The self object.
     */
    public Parameters setArray(String name, Array value) {
        return setValue(name, value);
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

    AllocSlice encode() throws LiteCoreException {
        Encoder encoder = new Encoder();
        encoder.write(map);
        return encoder.finish();
    }

}
