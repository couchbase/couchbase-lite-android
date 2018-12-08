//
// DocumentReplicatedUpdate.java
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

/**
 * Document replicated update of a replicator.
 */
public final class DocumentReplicatedUpdate {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Replicator replicator;
    private boolean completed = false;
    private boolean isDeleted = false;
    private boolean pushing = false;
    private String docId = "";

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    DocumentReplicatedUpdate(Replicator replicator, boolean completed, boolean isDeleted, boolean pushing, String docId) {
        this.replicator = replicator;
        this.completed = completed;
        this.pushing = pushing;
        this.docId = docId;
        this.isDeleted = isDeleted;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Return the source replicator object.
     */
    public Replicator getReplicator() {
        return replicator;
    }

    /**
     * The current document replicated flag.
     */
    public boolean getCompletedFlag() {
        return completed;
    }

    /**
     * The current document replication direction flag.
     */
    public boolean getPushingFlag() {
        return pushing;
    }

    /**
     * The current document id.
     */
    public String getDocId() {
        return docId;
    }

    /**
     * The current document id.
     */
    public boolean getIsDeleted() {
        return isDeleted;
    }


    @Override
    public String toString() {
        return "DocumentReplicatedUpdate{" +
                "replicator=" + replicator +
                "is completed =" + completed +
                ", is pushing =" + pushing +
                ", document id =" + docId +
                ", doc is deleted =" + isDeleted +
                '}';
    }

    DocumentReplicatedUpdate copy() {
        return new DocumentReplicatedUpdate(replicator, completed, isDeleted, pushing, docId);
    }
}
