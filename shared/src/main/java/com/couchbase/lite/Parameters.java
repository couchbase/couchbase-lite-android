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

import android.support.annotation.NonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.Encoder;


/**
 * A Parameters object used for setting values to the query parameters defined in the query.
 */
public final class Parameters {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final Map<String, Object> map;
    private boolean readonly;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public Parameters() { this(null); }

    public Parameters(Parameters parameters) {
        map = (parameters == null) ? new HashMap<>() : new HashMap<>(parameters.map);
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
    public Parameters setValue(@NonNull String name, Object value) {
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        if (readonly) { throw new IllegalStateException("Parameters is readonly mode."); }
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
    @NonNull
    public Parameters setString(@NonNull String name, String value) {
        return setValue(name, value);
    }

    /**
     * Set an Number value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Number value.
     * @return The self object.
     */
    @NonNull
    public Parameters setNumber(@NonNull String name, Number value) {
        return setValue(name, value);
    }

    /**
     * Set an int value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    public Parameters setInt(@NonNull String name, int value) {
        return setValue(name, value);
    }

    /**
     * Set an long value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    public Parameters setLong(@NonNull String name, long value) {
        return setValue(name, value);
    }

    /**
     * Set a float value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    public Parameters setFloat(@NonNull String name, float value) {
        return setValue(name, value);
    }

    /**
     * Set a double value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDouble(@NonNull String name, double value) {
        return setValue(name, value);
    }

    /**
     * Set a boolean value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    public Parameters setBoolean(@NonNull String name, boolean value) {
        return setValue(name, value);
    }

    /**
     * Set a date value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The date value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDate(@NonNull String name, Date value) {
        return setValue(name, value);
    }

    /**
     * Set the Blob value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Blob value.
     * @return The self object.
     */
    @NonNull
    public Parameters setBlob(@NonNull String name, Blob value) {
        return setValue(name, value);
    }

    /**
     * Set the Dictionary value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Dictionary value.
     * @return The self object.
     */
    @NonNull
    public Parameters setDictionary(@NonNull String name, Dictionary value) {
        return setValue(name, value);
    }

    /**
     * Set the Array value to the query parameter referenced by the given name. A query parameter
     * is defined by using the Expression's parameter(String name) function.
     *
     * @param name  The parameter name.
     * @param value The Array value.
     * @return The self object.
     */
    @NonNull
    public Parameters setArray(@NonNull String name, Array value) {
        return setValue(name, value);
    }

    /**
     * Gets a parameter's value.
     *
     * @param name The parameter name.
     * @return The parameter value.
     */
    public Object getValue(@NonNull String name) {
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        return map.get(name);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    Parameters readonlyCopy() {
        final Parameters parameters = new Parameters(this);
        parameters.readonly = true;
        return parameters;
    }

    AllocSlice encode() throws LiteCoreException {
        final Encoder encoder = new Encoder();
        encoder.write(map);
        return encoder.finish();
    }
}
