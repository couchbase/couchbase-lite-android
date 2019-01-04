//
// ReplicatedDocument.java
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

public final class ReplicatedDocument {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private DocumentFlags documentFlagsflags;
    private String id = "";
    private C4Error error;
    private boolean trans;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Document replicated update of a replicator.
     */
    ReplicatedDocument(String id, DocumentFlags flags, C4Error error, boolean trans) {
        this.id = id;
        this.documentFlagsflags = flags;
        this.error = error;
        this.trans = trans;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The current document id.
     */
    public String getID() {
        return id;
    }

    /**
     * The current status flag of the document. eg. deleted, access removed
     */
    public DocumentFlags flags() { return documentFlagsflags; }

    /**
     * The current document replication error.
     */
    public CouchbaseLiteException getError() {
        return error.getCode() != 0 ? CBLStatus.convertError(error) : null;
    }

    @Override
    public String toString() {
        return "ReplicatedDocument {" +
                ", document id =" + id +
                ", error code =" + error.getCode()+
                ", error domain=" + error.getDomain() +
                '}';
    }
}

