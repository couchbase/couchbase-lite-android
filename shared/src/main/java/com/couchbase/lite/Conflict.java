//
// Conflict.java
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

/**
 * Provides details about a Conflict.
 */
public final class Conflict {
    private Document mine;
    private Document theirs;
    private Document base;

    Conflict(Document mine, Document theirs, Document base) {
        this.mine = mine;
        this.theirs = theirs;
        this.base = base;
    }

    /**
     * Return the mine version of the document.
     */
    public Document getMine() {
        return mine;
    }

    /**
     * Return the theirs version of the document.
     */
    public Document getTheirs() {
        return theirs;
    }

    /**
     * Return the base or common anchester version of the document.
     */
    public Document getBase() {
        return base;
    }
}
