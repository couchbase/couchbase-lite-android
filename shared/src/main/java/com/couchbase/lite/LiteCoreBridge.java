/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.litecore.LiteCoreException;

final class LiteCoreBridge {
    private LiteCoreBridge() {

    }

    static CouchbaseLiteException convertException(LiteCoreException orgEx) {
        return new CouchbaseLiteException(orgEx.getMessage(), orgEx, orgEx.domain, orgEx.code);
    }

    static CouchbaseLiteRuntimeException convertRuntimeException(LiteCoreException orgEx) {
        return new CouchbaseLiteRuntimeException(orgEx.getMessage(), orgEx, orgEx.domain, orgEx.code);
    }
}
