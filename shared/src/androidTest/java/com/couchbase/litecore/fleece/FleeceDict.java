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
package com.couchbase.litecore.fleece;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FleeceDict implements Map<String, Object>, Encodable {
    private MDict _dict;

    private FleeceDict() {
        _dict = new MDict();
    }

    // Call from native method
    FleeceDict(MValue mv, MCollection parent) {
        this();
        _dict.initInSlot(mv, parent);
    }

    public boolean isMutated() {
        return _dict.isMutated();
    }

    @Override
    public int size() {
        return (int) _dict.count();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object o) {
        if (!(o instanceof String)) return false;
        return _dict.contains((String) o);
    }

    @Override
    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String)) return null;
        return _dict.get((String) key).asNative(_dict);
    }

    @Override
    public Object put(String key, Object o) {
        Object prev = null;
        if (_dict.contains(key))
            prev = _dict.get(key);
        _dict.set(key, new MValue(o));
        return prev;
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String)) return null;
        Object prev = null;
        if (_dict.contains((String) key))
            prev = get(key);
        _dict.remove((String) key);
        return prev;
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        _dict.clear();
    }

    @Override
    public Set<String> keySet() {
        return new HashSet<String>(_dict.getKeys());
    }

    @Override
    public Collection<Object> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        throw new UnsupportedOperationException();
    }

    // Implementation of FLEncodable
    @Override
    public void encodeTo(Encoder enc) {
        _dict.encodeTo(enc);
    }

    // For MValue

    MCollection toMCollection() {
        return _dict;
    }
}
