//
// DocumentReplication.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.util.List;


/**
 * Document replicated update of a replicator.
 */
public final class DocumentReplication {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Replicator replicator;
    private final List<ReplicatedDocument> documents;
    private final boolean pushing;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    DocumentReplication(Replicator replicator, boolean isPush, List<ReplicatedDocument> documents) {
        this.replicator = replicator;
        this.pushing = isPush;
        this.documents = documents;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source replicator object.
     */
    @NonNull
    public Replicator getReplicator() {
        return replicator;
    }

    /**
     * The current document replication direction flag.
     */
    public boolean isPush() {
        return pushing;
    }

    @NonNull
    public List<ReplicatedDocument> getDocuments() { return documents; }
}



