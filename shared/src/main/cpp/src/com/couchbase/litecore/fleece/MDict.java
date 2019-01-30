//
// MDict.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MDict extends MCollection implements Iterable<String> {
    private FLDict _dict;

    private Map<String, MValue> _map = new HashMap<String, MValue>();

    private final List<String> _newKey = new ArrayList<String>();

    private long _count = 0;

    /* Constructors */

    public MDict() {

    }

    public void initInSlot(MValue mv, MCollection parent) {
        initInSlot(mv, parent, parent != null ? parent.getMutableChildren() : false);
    }

    @Override
    protected void initInSlot(MValue mv, MCollection parent, boolean isMutable) {
        super.initInSlot(mv, parent, isMutable);
        if (_dict != null)
            throw new IllegalStateException("_dict is not null");

        FLValue value = mv.getValue();
        if (value != null) {
            _dict = value.asFLDict();
            _count = _dict.count();
        } else {
            _dict = null;
            _count = 0;
        }
    }

    public void initAsCopyOf(MDict d, boolean isMutable) {
        super.initAsCopyOf(d, isMutable);
        _dict = d._dict;
        _map = new HashMap<>(d._map);
        _count = d._count;
    }

    /* Properties */

    public long count() {
        return _count;
    }

    /* Public methods */

    public boolean clear() {
        if (!isMutable())
            throw new IllegalStateException("Cannot clear a non-mutable MDict");

        if (_count == 0)
            return true;

        mutate();
        _map.clear();

        if (_dict != null) {
            FLDictIterator itr = new FLDictIterator();
            try {
                itr.begin(_dict);
                String key;
                while ((key = itr.getKeyString()) != null) {
                    _map.put(key, MValue.EMPTY);
                    if (!itr.next())
                        break;
                }
            } finally {
                itr.free();
            }
        }

        _count = 0;
        return true;
    }

    public boolean contains(String key) {
        if (key == null)
            throw new IllegalArgumentException("The key cannot be null.");

        MValue v = _map.get(key);
        if (v != null)
            return !v.isEmpty();
        else
            return _dict != null && _dict.get(key) != null;
    }

    public MValue get(String key) {
        if (key == null)
            throw new IllegalArgumentException("The key cannot be null.");

        MValue v = _map.get(key);
        if (v == null) {
            FLValue value = _dict != null ? _dict.get(key) : null;
            v = value != null ? setInMap(key, new MValue(value)) : MValue.EMPTY;
        }
        return v;
    }

    public List<String> getKeys() {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, MValue> entry : _map.entrySet()) {
            MValue value = entry.getValue();
            if (!value.isEmpty())
                keys.add(entry.getKey());
        }

        if (_dict != null) {
            FLDictIterator itr = new FLDictIterator();
            try {
                itr.begin(_dict);
                if (itr.getCount() > 0) {
                    String key;
                    while ((key = itr.getKeyString()) != null) {
                        if (!_map.containsKey(key))
                            keys.add(key);
                        if (!itr.next())
                            break;
                    }
                }
            } finally {
                itr.free();
            }
        }

        return keys;
    }

    public boolean remove(String key) {
        return set(key, MValue.EMPTY);
    }

    public boolean set(String key, MValue value) {
        if (key == null)
            throw new IllegalArgumentException("The key cannot be null.");

        if (!isMutable())
            throw new IllegalStateException("Cannot set items in a non-mutable MDict");

        MValue oValue = _map.get(key);
        if (oValue != null) {
            // Found in _map; update value:
            if (value.isEmpty() && oValue.isEmpty())
                return true; // no-op
            mutate();
            _count += (value.isEmpty() ? 0 : 1) - (oValue.isEmpty() ? 0 : 1);
            _map.put(key, value);
        } else {
            // Not found; check _dict:
            if (_dict != null && _dict.get(key) != null) {
                if (value.isEmpty())
                    _count--;
            } else {
                if (value.isEmpty())
                    return true; // no-op
                else
                    _count++;
            }

            mutate();
            setInMap(key, value);
        }
        return true;
    }

    /* Private Methods */

    private MValue setInMap(String key, MValue value) {
        _newKey.add(key);
        _map.put(key, value);
        return value;
    }

    /* Encodable */

    @Override
    public void encodeTo(Encoder enc) {
        if (!isMutated()) {
            if (_dict == null) {
                enc.beginDict(0);
                enc.endDict();
            } else
                enc.writeValue(_dict);
        } else {
            enc.beginDict(_count);
            for (Map.Entry<String, MValue> entry : _map.entrySet()) {
                MValue value = entry.getValue();
                if (!value.isEmpty()) {
                    enc.writeKey(entry.getKey());
                    value.encodeTo(enc);
                }
            }

            if (_dict != null) {
                FLDictIterator itr = new FLDictIterator();
                try {
                    itr.begin(_dict);
                    String key;
                    while ((key = itr.getKeyString()) != null) {
                        if (!_map.containsKey(key)) {
                            enc.writeKey(key);
                            enc.writeValue(itr.getValue());
                        }
                        if (!itr.next())
                            break;
                    }
                } finally {
                    itr.free();
                }
            }
            enc.endDict();
        }
    }

    /* Iterable */

    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }
}
