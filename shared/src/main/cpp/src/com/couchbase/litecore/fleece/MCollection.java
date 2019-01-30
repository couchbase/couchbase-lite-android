//
// MCollection.java
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

public abstract class MCollection implements Encodable {
    private MValue _slot;

    private MContext _context;

    private boolean _isMutable;

    private boolean _isMutated;

    private boolean _mutableChildren;

    private MCollection _parent;

    /* Constructors */

    protected MCollection() {
        this(MContext.NULL, true);
    }

    protected MCollection(MContext context, boolean isMutable) {
        _context = context;
        _isMutable = isMutable;
        _mutableChildren = isMutable;
    }

    /* Properties */

    public MContext getContext() {
        return _context;
    }

    public boolean isMutable() {
        return _isMutable;
    }

    public boolean isMutated() {
        return _isMutated;
    }

    /* Public Methods */

    public boolean getMutableChildren() {
        return _mutableChildren;
    }

    public void initAsCopyOf(MCollection original, boolean isMutable) {
        if (_context != MContext.NULL)
            throw new IllegalStateException("Current context is not null.");

        _context = original.getContext();
        _isMutable = isMutable;
        _mutableChildren = isMutable;
    }

    /* Protected Methods */

    protected void setSlot(MValue newSlot, MValue oldSlot) {
        if (_slot.equals(oldSlot)) {
            _slot = newSlot;
            if (newSlot == null)
                _parent = null;
        }
    }

    protected void initInSlot(MValue slot, MCollection parent, boolean isMutable) {
        if (slot == null)
            throw new IllegalArgumentException("slot cannot be null.");
        if (_context != MContext.NULL)
            throw new IllegalStateException("Current context is not MContext.Null");

        _slot = slot;
        _parent = parent;
        _isMutable = isMutable;
        _mutableChildren = isMutable;
        _isMutated = _slot.isMutated();
        if (_slot.getValue() != null)
            _context = parent != null ? parent.getContext() : null;
    }

    protected void mutate() {
        if (!_isMutable)
            throw new IllegalStateException("The collection object is not mutable.");
        if (!_isMutated) {
            _isMutated = true;
            if (_slot != null)
                _slot.mutate();
            if (_parent != null)
                _parent.mutate();
        }

    }
}
