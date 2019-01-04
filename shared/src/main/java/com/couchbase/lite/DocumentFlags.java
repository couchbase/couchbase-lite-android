//
// DocumentFlags.java
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

import com.couchbase.litecore.C4Constants;

public class DocumentFlags {

    private int _rawValue;

    public DocumentFlags(int rawValue){
        this._rawValue = rawValue;
    }

    /**
     * The current deleted status of the document.
    */
    public boolean deleted() { return (_rawValue & C4Constants.C4RevisionFlags.kRevDeleted) != 0; }

    /**
     * The current access removed status of the document.
     */
    public boolean accessRemoved() { return (_rawValue & C4Constants.C4RevisionFlags.kRevPurged) != 0; }

    @Override
    public String toString() {
        return "DocumentFlags{" +
                " deleted='" + deleted() +
                ", accessRemoved='" + accessRemoved() +
                '}';
    }
}
