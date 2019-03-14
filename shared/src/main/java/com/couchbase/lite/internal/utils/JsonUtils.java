//
// JsonUtils.java
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
package com.couchbase.lite.internal.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public final class JsonUtils {
    @SuppressWarnings("unchecked")
    public static JSONObject toJson(Map<String, Object> map) throws JSONException {
        if (map == null) { return null; }
        final JSONObject json = new JSONObject();
        final Map<String, Object> m = (Map<String, Object>) map;
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            if (entry.getValue() == null) { json.put(entry.getKey(), JSONObject.NULL); }
            else if (entry.getValue() instanceof Map) { json.put(entry.getKey(), toJson((Map) entry.getValue())); }
            else if (entry.getValue() instanceof List) { json.put(entry.getKey(), toJson((List) entry.getValue())); }
            else { json.put(entry.getKey(), entry.getValue()); }
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    public static JSONArray toJson(List<Object> list) throws JSONException {
        if (list == null) { return null; }
        final JSONArray json = new JSONArray();
        for (Object value : list) {
            if (value == null) { json.put(JSONObject.NULL); }
            else if (value instanceof Map) { json.put(toJson((Map) value)); }
            else if (value instanceof List) { json.put(toJson((List) value)); }
            else { json.put(value); }
        }
        return json;
    }

    public static Map<String, Object> fromJson(JSONObject json) throws JSONException {
        if (json == null) { return null; }
        final Map<String, Object> result = new HashMap<>();
        final Iterator<String> itr = json.keys();
        while (itr.hasNext()) {
            final String key = itr.next();
            final Object value = json.get(key);
            if (value instanceof JSONObject) { result.put(key, fromJson((JSONObject) value)); }
            else if (value instanceof JSONArray) { result.put(key, fromJson((JSONArray) value)); }
            else { result.put(key, value); }
        }
        return result;
    }

    public static List<Object> fromJson(JSONArray json) throws JSONException {
        if (json == null) { return null; }
        final List<Object> result = new ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            final Object value = json.get(i);
            if (value instanceof JSONObject) { result.add(fromJson((JSONObject) value)); }
            else if (value instanceof JSONArray) { result.add(fromJson((JSONArray) value)); }
            else { result.add(value); }
        }
        return result;
    }

    private JsonUtils() { }
}
