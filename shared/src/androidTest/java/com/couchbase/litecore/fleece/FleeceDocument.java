//
// FleeceDocument.java
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

public class FleeceDocument {
    private MRoot _root = null;

    FleeceDocument(AllocSlice fleeceData, boolean mutableContainers) {
        _root = new MRoot(fleeceData, mutableContainers);
    }

    public static Object getObject(AllocSlice fleeceData, boolean mutableContainers) {
        MRoot root = new MRoot(fleeceData, mutableContainers);
        return root.asNative();
    }

    public Object rootObject() {
        return _root.asNative();
    }

    @Override
    protected void finalize() throws Throwable {
        if (_root != null) {
            _root = null;
        }
        super.finalize();
    }
}
