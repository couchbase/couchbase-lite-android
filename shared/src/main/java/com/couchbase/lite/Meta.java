//
// Meta.java
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

import android.support.annotation.NonNull;


/**
 * Meta is a factory class for creating the expressions that refer to
 * the metadata properties of the document.
 */
@SuppressWarnings("ConstantName")
public class Meta {
    /**
     * A metadata expression referring to the ID of the document.
     */
    @NonNull
    public static final MetaExpression id = new MetaExpression("_id", "id", null);
    //---------------------------------------------
    // API - public static variables
    //---------------------------------------------
    /**
     * A metadata expression refering to the sequence number of the document.
     * The sequence number indicates how recently the document has been changed. If one document's
     * `sequence` is greater than another's, that means it was changed more recently.
     */
    @NonNull
    public static final MetaExpression sequence = new MetaExpression("_sequence", "sequence", null);
    /**
     * A metadata expression refering to the deleted boolean flag of the document.
     */
    @NonNull
    public static final MetaExpression deleted = new MetaExpression("_deleted", "deleted", null);
    /**
     * A metadata expression refering to the expiration date of the document.
     */
    @NonNull
    public static final MetaExpression expiration = new MetaExpression("_expiration", "expiration", null);

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    private Meta() { }
}
