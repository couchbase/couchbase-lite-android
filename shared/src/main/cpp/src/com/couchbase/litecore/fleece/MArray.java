//
// MArray.java
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
import java.util.List;

public class MArray extends MCollection {
    private List<MValue> _values = new ArrayList<MValue>();

    private FLArray _baseArray;

    /* Constructors */

    public MArray() {

    }

    public void initInSlot(MValue mv, MCollection parent) {
        initInSlot(mv, parent, parent != null ? parent.getMutableChildren() : false);
    }

    @Override
    protected void initInSlot(MValue mv, MCollection parent, boolean isMutable) {
        super.initInSlot(mv, parent, isMutable);
        if (_baseArray != null)
            throw new IllegalStateException("base array is not null.");

        FLValue value = mv.getValue();
        if (value != null) {
            _baseArray = value.asFLArray();
            resize(_baseArray.count());
        } else {
            _baseArray = null;
            resize(0);
        }
    }

    public void initAsCopyOf(MArray array, boolean isMutable) {
        super.initAsCopyOf(array, isMutable);
        _baseArray = array != null ? array.getBaseArray() : null;
        _values = array != null ? new ArrayList<>(array._values) : new ArrayList<MValue>();
    }

    /* Properties */

    public long count() {
        return _values.size();
    }


    public FLArray getBaseArray() {
        return _baseArray;
    }

    /* Public Methods */

    /**
     * Returns a reference to the MValue of the item at the given index.
     * If the index is out of range, returns an empty MValue.
     */
    public MValue get(long index) {
        if (index < 0 || index >= _values.size())
            return MValue.EMPTY;

        MValue value = _values.get((int) index);
        if (value.isEmpty()) {
            if (_baseArray != null) {
                value = new MValue(_baseArray.get(index));
                _values.set((int) index, value);
            }
        }
        return value;
    }

    public boolean set(long index, Object value) {
        if (!isMutable())
            throw new IllegalStateException("Cannot set items in a non-mutable MArray");

        if (index < 0 || index >= count())
            return false;

        mutate();
        _values.set((int) index, new MValue(value));
        return true;
    }

    public boolean insert(long index, Object value) {
        if (!isMutable())
            throw new IllegalStateException("Cannot insert items in a non-mutable MArray");

        if (index < 0 || index > count())
            return false;

        if (index < count())
            populateValues();

        mutate();
        _values.add((int) index, new MValue(value));
        return true;
    }

    public boolean append(Object value) {
        return insert(count(), value);
    }

    public boolean remove(long start, long num) {
        if (!isMutable())
            throw new IllegalStateException("Cannot remove items in a non-mutable MArray");

        long end = start + num;
        if (end <= start)
            return end == start;

        long count = count();
        if (end > count)
            return false;

        if (end < count)
            populateValues();

        mutate();
        _values.subList((int) start, (int) end).clear();
        return true;
    }

    public boolean remove(long index) {
        return remove(index, 1);
    }

    public boolean clear() {
        if (!isMutable())
            throw new IllegalStateException("Cannot clear items in a non-mutable MArray");

        if (_values.isEmpty())
            return true;

        mutate();
        _values.clear();
        return true;
    }

    /* Private Methods */

    void resize(long newSize) {
        int count = _values.size();
        if (newSize < count)
            _values.subList((int) newSize, count).clear();
        else if (newSize > count) {
            for (int i = 0; i < newSize - count; i++)
                _values.add(MValue.EMPTY);
        }
    }

    void populateValues() {
        if (_baseArray == null)
            return;

        int size = _values.size();
        for (int i = 0; i < size; i++) {
            MValue v = _values.get(i);
            if (v.isEmpty()) {
                _values.set(i, new MValue(_baseArray.get(i)));
            }
        }
    }

    public void encodeTo(Encoder enc) {
        if (!isMutated()) {
            if (_baseArray == null) {
                enc.beginArray(0);
                enc.endArray();
            } else
                enc.writeValue(_baseArray);
        } else {
            enc.beginArray(count());
            long i = 0;
            for (MValue value : _values) {
                if (value.isEmpty())
                    enc.writeValue(_baseArray.get(i));
                else
                    value.encodeTo(enc);
                i++;
            }
            enc.endArray();
        }
    }
}
