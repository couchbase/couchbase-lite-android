//
// LiteCoreException.java
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


public class LiteCoreException extends Exception {
    // NOTE called to throw LiteCoreException from native code to Java
    public static void throwException(int domain, int code, String msg) throws LiteCoreException {
        throw new LiteCoreException(domain, code, msg);
    }
    public final int domain; // TODO: Should be an enum
    public final int code;

    public LiteCoreException(int domain, int code, String message) {
        super(message);
        this.domain = domain;
        this.code = code;
    }

    public int getDomain() {
        return domain;
    }

    public int getCode() {
        return code;
    }

    @NonNull
    @Override
    public String toString() {
        return "LiteCoreException{" +
            "domain=" + domain +
            ", code=" + code +
            ", msg=" + super.getMessage() +
            '}';
    }
}
