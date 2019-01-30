//
// C4Error.java
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
package com.couchbase.litecore;

public class C4Error {
    private int domain = 0;        // C4Error.domain
    private int code = 0;          // C4Error.code
    private int internalInfo = 0;  // C4Error.internal_info

    public C4Error() {
        domain = 0;        // C4Error.domain
        code = 0;          // C4Error.code
        internalInfo = 0;
    }

    public C4Error(int domain, int code, int internalInfo) {
        this.domain = domain;
        this.code = code;
        this.internalInfo = internalInfo;
    }

    public int getDomain() {
        return domain;
    }

    public int getCode() {
        return code;
    }

    public int getInternalInfo() {
        return internalInfo;
    }

    @Override
    public String toString() {
        return "C4Error{" +
                "domain=" + domain +
                ", code=" + code +
                ", internalInfo=" + internalInfo +
                '}';
    }
}
