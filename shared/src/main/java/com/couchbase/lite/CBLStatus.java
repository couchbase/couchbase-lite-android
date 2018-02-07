//
// CBLStatus.java
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

import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.LiteCoreException;

class CBLStatus {
    final static String[] kErrorDomains = {
            null,
            CBLError.Domain.CBLErrorDomain,     // LiteCoreDomain
            "POSIXErrorDomain", // POSIXDomain
            null,               // ForestDBDomain
            CBLError.Domain.SQLiteErrorDomain,  // SQLiteDomain
            CBLError.Domain.FleeceErrorDomain,  // FleeceDomain
            "NetworkDomain",    // network error
            CBLError.Domain.CBLErrorDomain};    // WebSocketDomain

    static CouchbaseLiteException convertException(int _domain, int _code, LiteCoreException e) {
        String domain = kErrorDomains[_domain];
        int code = _code;
        if (_domain == C4Constants.C4ErrorDomain.WebSocketDomain)
            code += CBLError.Code.CBLErrorHTTPBase;

        if (domain == null) {
            Log.w(Log.DATABASE, "Unable to map C4Error(%d,%d) to an CouchbaseLiteException", _domain, _code);
            domain = CBLError.Domain.CBLErrorDomain;
            code = CBLError.Code.CBLErrorUnexpectedError;
        }
        if (e != null)
            return new CouchbaseLiteException(e.getMessage(), e, domain, code);
        else
            return new CouchbaseLiteException(domain, code);
    }

    static CouchbaseLiteException convertException(LiteCoreException e) {
        return convertException(e.domain, e.code, e);
    }

    static CouchbaseLiteRuntimeException convertRuntimeException(LiteCoreException e) {
        return convertRuntimeException(e.domain, e.code, e);
    }

    static CouchbaseLiteRuntimeException convertRuntimeException(int _domain, int _code, LiteCoreException e) {
        String domain = kErrorDomains[_domain];
        int code = _code;
        if (_domain == C4Constants.C4ErrorDomain.WebSocketDomain)
            code += CBLError.Code.CBLErrorHTTPBase;

        if (domain == null) {
            Log.w(Log.DATABASE, "Unable to map C4Error(%d,%d) to an CouchbaseLiteRuntimeException", _domain, _code);
            domain = CBLError.Domain.CBLErrorDomain;
            code = CBLError.Code.CBLErrorUnexpectedError;
        }
        if (e != null)
            return new CouchbaseLiteRuntimeException(e.getMessage(), e, domain, code);
        else
            return new CouchbaseLiteRuntimeException(domain, code);
    }
}
