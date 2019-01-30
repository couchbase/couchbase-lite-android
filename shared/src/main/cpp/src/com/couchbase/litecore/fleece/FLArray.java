//
// FLArray.java
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

public class FLArray {
    private long handle = 0; // hold pointer to FLArray

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public FLArray(long handle) {
        this.handle = handle;
    }

    public List<Object> asArray() {
        List<Object> results = new ArrayList<>();
        FLArrayIterator itr = new FLArrayIterator();
        try {
            itr.begin(this);
            FLValue value;
            while ((value = itr.getValue()) != null) {
                results.add(value.asObject());
                if (!itr.next())
                    break;
            }
        } finally {
            itr.free();
        }
        return results;
    }

    public long count() {
        return count(handle);
    }

    public FLValue get(long index) {
        return new FLValue(get(handle, index));
    }

    //-------------------------------------------------------------------------
    // package level access
    //-------------------------------------------------------------------------
    long getHandle() {
        return handle;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Returns the number of items in an array, or 0 if the pointer is nullptr.
     *
     * @param array FLArray
     * @return long (uint32_t)
     */
    static native long count(long array);

    /**
     * Returns an value at an array index, or nullptr if the index is out of range.
     *
     * @param array FLArray
     * @param index uint32_t
     * @return long (FLValue)
     */
    static native long get(long array, long index);

    // TODO: Need free()?
}
