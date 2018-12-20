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
    private boolean isDeleted = false;
    private boolean isAccessRemoved = false;
    private String id = "";
    private String revId = "";
    private C4Error error;
    private boolean trans;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    ReplicatedDocument(boolean isDeleted, boolean isAccessRemoved, String id, String revId, C4Error error, boolean trans) {
        this.id = id;
        this.isDeleted = isDeleted;
        this.isAccessRemoved = isAccessRemoved;
        this.revId = revId;
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
    }

    /**
     * The current document replication error.
     */
    public CouchbaseLiteException getError() {
        return error.getCode() != 0 ? CBLStatus.convertError(error) : null;
    }

    @Override
    public String toString() {
        return "DocumentReplication {" +
                ", document id =" + id +
                ", error code =" + error.getCode()+
                ", error domain=" + error.getDomain() +
                ", doc is deleted =" + isDeleted +
                ", doc is isAccessRemoved =" + isAccessRemoved +
                '}';
    }

    ReplicatedDocument copy() {
        return new ReplicatedDocument(isDeleted, isAccessRemoved, id, revId, new C4Error(error.getDomain(), error.getCode(), error.getInternalInfo()), trans);
    }
}

