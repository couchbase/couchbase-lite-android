//
// MValue.java
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

import java.util.concurrent.atomic.AtomicBoolean;


public class MValue implements Encodable {
    public static final MValue EMPTY = new MValue(true);

    public interface Delegate {
        Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt);

        MCollection collectionFromNative(Object object);

        void encodeNative(Encoder encoder, Object object);
    }
    private static Delegate delegate;

    public static void registerDelegate(Delegate delegate) {
        if (delegate == null) { throw new IllegalArgumentException("delegate cannot be null."); }
        MValue.delegate = delegate;
    }

    private static void checkDelegate() {
        if (delegate == null) { throw new IllegalStateException("No MValue delegation defined"); }
    }
    private boolean empty;

    /* Delegate */
    private Object nativeObject;

    /* Constructors */
    private FLValue value;

    public MValue(boolean isEmpty) {
        this.empty = isEmpty;
    }

    public MValue(Object value) {
        nativeObject = value;
    }

    /* Properties */

    public MValue(FLValue value) {
        this.value = value;
    }

    public boolean isEmpty() {
        return empty;
    }

    public boolean isMutated() {
        return value == null;
    }

    /* Public Methods */

    public FLValue getValue() {
        return value;
    }

    public Object asNative(MCollection parent) {
        if (nativeObject != null || value == null) { return nativeObject; }

        final AtomicBoolean cacheIt = new AtomicBoolean(false);
        final Object obj = toNative(this, parent, cacheIt);
        if (cacheIt.get()) { nativeObject = obj; }
        return obj;
    }

    /* Private Methods */

    public void mutate() {
        if (nativeObject == null) { throw new IllegalStateException("Native object is null."); }
        value = null;
    }

    private Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt) {
        checkDelegate();
        return delegate.toNative(mv, parent, cacheIt);
    }

    private MCollection collectionFromNative(Object obj) {
        checkDelegate();
        return delegate.collectionFromNative(obj);
    }

    private void encodeNative(Encoder encoder, Object object) {
        checkDelegate();
        delegate.encodeNative(encoder, object);
    }

    private void nativeChangeSlot(MValue newSlot) {
        final MCollection collection = collectionFromNative(newSlot);
        if (collection != null) { collection.setSlot(newSlot, this); }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        nativeChangeSlot(null);
        super.finalize();
    }

    @Override
    public void encodeTo(Encoder enc) {
        if (empty) { throw new IllegalStateException("MValue is empty."); }

        if (value != null) { enc.writeValue(value); }
        else {
            encodeNative(enc, nativeObject);
        }
    }
}
