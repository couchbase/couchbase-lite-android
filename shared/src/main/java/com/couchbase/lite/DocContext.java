//
// DocContext.java
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
package com.couchbase.lite;

import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.MContext;

/**
 * This DocContext implementation is simplified version of lite-core DocContext implementation
 * by eliminating unused variables and methods
 */
class DocContext extends MContext {
    private Database _db;
    private C4Document _doc;

    DocContext(Database db, C4Document doc) {
        super(new AllocSlice("{}".getBytes()));
        _db = db;
        _doc = doc;
        if (_doc != null)
            _doc.retain();
    }

    Database getDatabase() {
        return _db;
    }

    @Override
    protected void finalize() throws Throwable {
        if (_doc != null)
            _doc.release();
        super.finalize();
    }
}
