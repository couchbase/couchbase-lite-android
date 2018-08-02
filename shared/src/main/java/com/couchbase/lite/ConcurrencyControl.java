//
// ConcurrencyControl.java
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
 * ConcurrencyControl type used when saving or deleting a document.
 */
public enum ConcurrencyControl {
    /**
     * The last write operation will win if there is a conflict.
     */
    LAST_WRITE_WINS(0),
    /**
     * The operation will fail if there is a conflict.
     */
    FAIL_ON_CONFLICT(1);

    private final int value;

    ConcurrencyControl(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
