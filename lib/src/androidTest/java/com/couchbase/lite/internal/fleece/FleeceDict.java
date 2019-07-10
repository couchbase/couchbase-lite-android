//
// FleeceDict.java
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
package com.couchbase.lite.internal.fleece;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class FleeceDict implements Map<String, Object>, Encodable {
    private MDict dict;

    private FleeceDict() {
        dict = new MDict();
    }

    // Call from native method
    FleeceDict(MValue mv, MCollection parent) {
        this();
        dict.initInSlot(mv, parent);
    }

    public boolean isMutated() {
        return dict.isMutated();
    }

    @Override
    public int size() {
        return (int) dict.count();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object o) {
        if (!(o instanceof String)) { return false; }
        return dict.contains((String) o);
    }

    @Override
    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String)) { return null; }
        return dict.get((String) key).asNative(dict);
    }

    @Override
    public Object put(String key, Object o) {
        Object prev = null;
        if (dict.contains(key)) { prev = dict.get(key); }
        dict.set(key, new MValue(o));
        return prev;
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String)) { return null; }
        Object prev = null;
        if (dict.contains((String) key)) { prev = get(key); }
        dict.remove((String) key);
        return prev;
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        dict.clear();
    }

    @Override
    public Set<String> keySet() {
        return new HashSet<String>(dict.getKeys());
    }

    @Override
    public Collection<Object> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entrySet = new HashSet<>();
        for (String key : dict.getKeys()) {
            entrySet.add(new AbstractMap.SimpleEntry<String, Object>(key, get(key)));
        }
        return entrySet;
    }

    // FLEncodable

    @Override
    public void encodeTo(FLEncoder enc) {
        dict.encodeTo(enc);
    }

    // MValue

    MCollection toMCollection() {
        return dict;
    }
}
