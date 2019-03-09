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
package com.couchbase.lite.internal.fleece;

public abstract class MCollection implements Encodable {
    private MValue slot;

    private MContext context;

    private boolean mutable;

    private boolean mutated;

    private boolean mutableChildren;

    private MCollection parent;

    /* Constructors */

    protected MCollection() {
        this(MContext.NULL, true);
    }

    protected MCollection(MContext context, boolean isMutable) {
        this.context = context;
        this.mutable = isMutable;
        mutableChildren = isMutable;
    }

    /* Properties */

    public MContext getContext() {
        return context;
    }

    public boolean isMutable() {
        return mutable;
    }

    public boolean isMutated() {
        return mutated;
    }

    /* Public Methods */

    public boolean hasMutableChildren() {
        return mutableChildren;
    }

    public void initAsCopyOf(MCollection original, boolean isMutable) {
        if (context != MContext.NULL) { throw new IllegalStateException("Current context is not null."); }

        context = original.getContext();
        this.mutable = isMutable;
        mutableChildren = isMutable;
    }

    /* Protected Methods */

    protected void setSlot(MValue newSlot, MValue oldSlot) {
        if (slot.equals(oldSlot)) {
            slot = newSlot;
            if (newSlot == null) { parent = null; }
        }
    }

    protected void initInSlot(MValue slot, MCollection parent, boolean isMutable) {
        if (slot == null) { throw new IllegalArgumentException("slot cannot be null."); }
        if (context != MContext.NULL) { throw new IllegalStateException("Current context is not MContext.Null"); }

        this.slot = slot;
        this.parent = parent;
        this.mutable = isMutable;
        mutableChildren = isMutable;
        mutated = this.slot.isMutated();
        if (this.slot.getValue() != null) { context = parent != null ? parent.getContext() : null; }
    }

    protected void mutate() {
        if (!mutable) { throw new IllegalStateException("The collection object is not mutable."); }
        if (!mutated) {
            mutated = true;
            if (slot != null) { slot.mutate(); }
            if (parent != null) { parent.mutate(); }
        }
    }
}
