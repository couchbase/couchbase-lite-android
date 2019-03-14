//
// DocumentFlag.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
 * The flags enum describing the replicated document.
 */
public enum DocumentFlag {

    /**
     * The current deleted status of the document.
     */
    DocumentFlagsDeleted(1),

    /**
     * The current access removed status of the document.
     */
    DocumentFlagsAccessRemoved(1 << 1);

    private final int rawValue;

    DocumentFlag(int rawValue) {
        this.rawValue = rawValue;
    }

    public int rawValue() {
        return rawValue;
    }
}
