/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.cblite;

import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectWriter;

import android.util.Log;

/**
 * A request/response/document body, stored as either JSON or a Map<String,Object>
 */
public class CBLBody {

    private byte[] json;
    private Object object;
    private boolean error = false;

    public CBLBody(byte[] json) {
        this.json = json;
    }

    public CBLBody(Map<String, Object> properties) {
        this.object = properties;
    }

    public CBLBody(List<?> array) {
        this.object = array;
    }

    public static CBLBody bodyWithProperties(Map<String,Object> properties) {
        CBLBody result = new CBLBody(properties);
        return result;
    }

    public static CBLBody bodyWithJSON(byte[] json) {
        CBLBody result = new CBLBody(json);
        return result;
    }

    public boolean isValidJSON() {
        // Yes, this is just like asObject except it doesn't warn.
        if(json == null && !error) {
            try {
                json = CBLServer.getObjectMapper().writeValueAsBytes(object);
            } catch (Exception e) {
                error = true;
            }
        }
        return (object != null);
    }

    public byte[] getJson() {
        if(json == null && !error) {
            try {
                json = CBLServer.getObjectMapper().writeValueAsBytes(object);
            } catch (Exception e) {
                Log.w(CBLDatabase.TAG, "CBLBody: couldn't convert JSON");
                error = true;
            }
        }
        return json;
    }

    public byte[] getPrettyJson() {
        Object properties = getObject();
        if(properties != null) {
            ObjectWriter writer = CBLServer.getObjectMapper().writerWithDefaultPrettyPrinter();
            try {
                json = writer.writeValueAsBytes(properties);
            } catch (Exception e) {
                error = true;
            }
        }
        return getJson();
    }

    public String getJSONString() {
        return new String(getJson());
    }

    public Object getObject() {
        if(object == null && !error) {
            try {
                if(json != null) {
                    object = CBLServer.getObjectMapper().readValue(json, Map.class);
                }
            } catch (Exception e) {
                Log.w(CBLDatabase.TAG, "CBLBody: couldn't parse JSON: " + new String(json), e);
                error = true;
            }
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProperties() {
        Object object = getObject();
        if(object instanceof Map) {
            return (Map<String,Object>)object;
        }
        return null;
    }

    public Object getPropertyForKey(String key) {
        Map<String,Object> theProperties = getProperties();
        return theProperties.get(key);
    }

    public boolean isError() {
        return error;
    }
}
