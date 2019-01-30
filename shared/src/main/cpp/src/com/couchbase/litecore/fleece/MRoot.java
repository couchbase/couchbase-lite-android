//
// MRoot.java
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

import com.couchbase.litecore.LiteCoreException;

public class MRoot extends MCollection {
    private MValue _slot;

    /* Constructors */

    public MRoot(MContext context, FLValue value, boolean isMutable) {
        super(context, isMutable);
        _slot = new MValue(value);
    }

    public MRoot(MContext context, boolean isMutable) {
        this(context, FLValue.fromData(context.getData()), isMutable);
    }

    public MRoot(AllocSlice fleeceData, boolean isMutable) {
        this(new MContext(fleeceData), isMutable);
    }

    public MRoot(AllocSlice fleeceData) {
        this(new MContext(fleeceData), true);
    }

    /* Properties */

    public boolean isMutated() {
        return _slot.isMutated();
    }

    /* Public Methods */

    public Object asNative() {
        return _slot.asNative(this);
    }

    public AllocSlice encode() throws LiteCoreException {
        Encoder encoder = new Encoder(new FLEncoder());
        try {
            _slot.encodeTo(encoder);
            return encoder.finish();
        } finally {
            encoder.release();
        }
    }

    /* Encodable */

    @Override
    public void encodeTo(Encoder enc) {
        _slot.encodeTo(enc);
    }
}
