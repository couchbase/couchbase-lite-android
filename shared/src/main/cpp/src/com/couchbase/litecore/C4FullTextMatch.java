//
// C4FullTextMatch.java
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
package com.couchbase.litecore;

import java.util.Arrays;
import java.util.List;

public class C4FullTextMatch {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    long _handle = 0L; // hold pointer to C4FullTextMatch

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4FullTextMatch(long handle) {
        this._handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public long dataSource() {
        return dataSource(_handle);
    }

    public long property() {
        return property(_handle);
    }

    public long term() {
        return term(_handle);
    }

    public long start() {
        return start(_handle);
    }

    public long length() {
        return length(_handle);
    }

    public List<Long> toList() {
        return Arrays.asList(dataSource(), property(), term(), start(), length());
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native long dataSource(long handle);

    static native long property(long handle);

    static native long term(long handle);

    static native long start(long handle);

    static native long length(long handle);
}
