//
// C4DatabaseChange.java
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
package com.couchbase.lite.internal.core;

public class C4DatabaseChange {
    private String docID;
    private String revID;
    private long sequence;
    private long bodySize;
    private boolean external;

    public String getDocID() {
        return docID;
    }

    public String getRevID() {
        return revID;
    }

    public long getSequence() {
        return sequence;
    }

    public long getBodySize() {
        return bodySize;
    }

    public boolean isExternal() {
        return external;
    }
}
