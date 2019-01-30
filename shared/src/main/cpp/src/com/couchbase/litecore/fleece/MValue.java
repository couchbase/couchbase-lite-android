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
package com.couchbase.litecore.fleece;

import java.util.concurrent.atomic.AtomicBoolean;

public class MValue implements Encodable {
    public interface Delegate {
        Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt);

        MCollection collectionFromNative(Object object);

        void encodeNative(Encoder encoder, Object object);
    }

    private static Delegate _delegate;

    public static final MValue EMPTY = new MValue(true);

    private boolean _isEmpty;

    private Object _nativeObject;

    private FLValue _value;

    /* Delegate */

    public static void registerDelegate(Delegate delegate) {
        if (delegate == null)
            throw new IllegalArgumentException("delegate cannot be null.");
        _delegate = delegate;
    }

    /* Constructors */

    public MValue(boolean isEmpty) {
        _isEmpty = isEmpty;
    }

    public MValue(Object value) {
        _nativeObject = value;
    }

    public MValue(FLValue value) {
        _value = value;
    }

    /* Properties */

    public boolean isEmpty() {
        return _isEmpty;
    }

    public boolean isMutated() {
        return _value == null;
    }

    public FLValue getValue() {
        return _value;
    }

    /* Public Methods */

    public Object asNative(MCollection parent) {
        if (_nativeObject != null || _value == null)
            return _nativeObject;

        AtomicBoolean cacheIt = new AtomicBoolean(false);
        Object obj = toNative(this, parent, cacheIt);
        if (cacheIt.get())
            _nativeObject = obj;
        return obj;
    }

    public void mutate() {
        if (_nativeObject == null)
            throw new IllegalStateException("Native object is null.");
        _value = null;
    }

    /* Private Methods */

    private static void checkDelegate() {
        if (_delegate == null)
            throw new IllegalStateException("No MValue delegation defined");
    }

    private Object toNative(MValue mv, MCollection parent, AtomicBoolean cacheIt) {
        checkDelegate();
        return _delegate.toNative(mv, parent, cacheIt);
    }

    private MCollection collectionFromNative(Object obj) {
        checkDelegate();
        return _delegate.collectionFromNative(obj);
    }

    private void encodeNative(Encoder encoder, Object object) {
        checkDelegate();
        _delegate.encodeNative(encoder, object);
    }

    private void nativeChangeSlot(MValue newSlot) {
        MCollection collection = collectionFromNative(newSlot);
        if (collection != null)
            collection.setSlot(newSlot, this);
    }

    @Override
    protected void finalize() throws Throwable {
        nativeChangeSlot(null);
        super.finalize();
    }

    @Override
    public void encodeTo(Encoder enc) {
        if (_isEmpty)
            throw new IllegalStateException("MValue is empty.");

        if (_value != null)
            enc.writeValue(_value);
        else {
            encodeNative(enc, _nativeObject);
        }
    }
}
