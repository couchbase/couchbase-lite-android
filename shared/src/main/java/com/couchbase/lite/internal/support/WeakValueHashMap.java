/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.internal.support;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class WeakValueHashMap<K, V> extends AbstractMap<K, V> {

    private HashMap<K, WeakValue<V>> references;
    private ReferenceQueue<V> referenceQueue;

    public WeakValueHashMap() {
        references = new HashMap<K, WeakValue<V>>();
        referenceQueue = new ReferenceQueue<V>();
    }

    public WeakValueHashMap(Map<? extends K, ? extends V> map) {
        this();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V put(K key, V value) {
        pruneDeadReferences();
        WeakValue<V> valueRef = new WeakValue<V>(key, value, referenceQueue);
        return getReferenceValue(references.put(key, valueRef));
    }

    @Override
    public V get(Object key) {
        pruneDeadReferences();
        return getReferenceValue(references.get(key));
    }

    @Override
    public V remove(Object key) {
        V value = getReferenceValue(references.get(key));
        references.remove(key);
        return value;
    }

    @Override
    public void clear() {
        references.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        pruneDeadReferences();
        return references.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        pruneDeadReferences();
        for (Map.Entry<K, WeakValue<V>> entry : references.entrySet()) {
            if (value == getReferenceValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<K> keySet() {
        pruneDeadReferences();
        return references.keySet();
    }

    @Override
    public int size() {
        pruneDeadReferences();
        return references.size();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        pruneDeadReferences();
        Set<Entry<K, V>> entries = new LinkedHashSet<Entry<K, V>>();
        for (Map.Entry<K, WeakValue<V>> entry : references.entrySet()) {
            entries.add(new AbstractMap.SimpleEntry<K, V>(entry.getKey(), getReferenceValue(entry.getValue())));
        }
        return entries;
    }

    public Collection<V> values() {
        pruneDeadReferences();
        Collection<V> values = new ArrayList<V>();
        for (WeakValue<V> valueRef : references.values()) {
            values.add(getReferenceValue(valueRef));
        }
        return values;
    }

    private V getReferenceValue(WeakValue<V> valueRef) {
        return valueRef == null ? null : valueRef.get();
    }

    private void pruneDeadReferences() {
        WeakValue<V> valueRef;
        while ((valueRef = (WeakValue<V>) referenceQueue.poll()) != null) {
            references.remove(valueRef.getKey());
        }
    }

    private class WeakValue<T> extends WeakReference<T> {
        private final K key;

        private WeakValue(K key, T value, ReferenceQueue<T> queue) {
            super(value, queue);
            this.key = key;
        }

        private K getKey() {
            return key;
        }
    }
}
