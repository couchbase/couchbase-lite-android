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

import com.couchbase.litecore.C4Error;

/**
 * Document replicated update of a replicator.
 */
public final class DocumentReplication {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Replicator replicator;
    private boolean isDeleted = false;
    private boolean isAccessRemoved = false;
    private boolean pushing = false;
    private String docId = "";
    private String revId = "";
    private int flags;
    private C4Error error;
    private boolean trans;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    DocumentReplication(Replicator replicator, boolean isDeleted, boolean pushing, String docId, String revId, int flags, C4Error error, boolean trans) {
        this.replicator = replicator;
        this.pushing = pushing;
        this.docId = docId;
        this.isDeleted = isDeleted;
        this.revId = revId;
        this.flags = flags;
        this.error = error;
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
     * The current document replication direction flag.
     */
    public boolean isPush() {
        return pushing;
    }

    /**
     * The current document id.
     */
    public String getDocumentID() {
        return docId;
    }

    /**
     * The current deleted status of the document.
     */
    public boolean isDeleted() {
        return isDeleted;
    }

    /**
     * The current access removed status of the document.
     */
    public boolean isAccessRemoved()  {
        return isAccessRemoved;
    } //TODO: will find a way to get this value and write unit test for it in the related PR for the ticket.

    /**
     * The current document replication error.
     */
    public CouchbaseLiteException getError() {
        return error.getCode() != 0 ? CBLStatus.convertError(error) : null;
    }

    @Override
    public String toString() {
        return "DocumentReplication {" +
                "replicator=" + replicator +
                ", is pushing =" + pushing +
                ", document id =" + docId +
                ", error code =" + error.getCode()+
                ", error domain=" + error.getDomain() +
                ", doc is deleted =" + isDeleted +
                '}';
    }

    DocumentReplication copy() {
        return new DocumentReplication(replicator, isDeleted, pushing, docId, revId, flags, new C4Error(error.getDomain(), error.getCode(), error.getInternalInfo()), trans);
    }
}
