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

import java.nio.charset.StandardCharsets;

import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.MContext;


/**
 * This DocContext implementation is simplified version of lite-core DocContext implementation
 * by eliminating unused variables and methods
 */
class DocContext extends MContext {
    private final Database db;
    private final C4Document doc;

    DocContext(Database db, C4Document doc) {
        super(new AllocSlice("{}".getBytes(StandardCharsets.UTF_8)));
        this.db = db;
        this.doc = doc;
        if (this.doc != null) { this.doc.retain(); }
    }

    Database getDatabase() {
        return db;
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        if (doc != null) { doc.release(); }
        super.finalize();
    }
}
