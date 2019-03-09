//
// C4DocumentEnded.java
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
package com.couchbase.lite.internal.core;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4DocumentEnded {
    private String docID;
    private String revID;
    private int flags;
    @SuppressFBWarnings("UUF_UNUSED_FIELD")
    private long sequence;
    private int errorDomain;         // C4Error.domain
    private int errorCode;           // C4Error.code
    private int errorInternalInfo;   // C4Error.internal_info
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private boolean errorIsTransient;

    public String getDocID() {
        return docID;
    }

    public String getRevID() {
        return revID;
    }

    public int getFlags() {
        return flags;
    }

    public int getErrorDomain() {
        return errorDomain;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getErrorInternalInfo() {
        return errorInternalInfo;
    }

    public C4Error getC4Error() {
        return new C4Error(errorDomain, errorCode, errorInternalInfo);
    }

    public boolean errorIsTransient() {
        return errorIsTransient;
    }

    @Override
    public String toString() {
        return "C4DocumentEnded{" +
            "doc id = " + docID +
            ", rev id = " + revID +
            ", flags = " + flags +
            ", error Is Transient = " + errorIsTransient +
            ", errorDomain=" + errorDomain +
            ", errorCode=" + errorCode +
            ", errorInternalInfo=" + errorInternalInfo +
            '}';
    }
}
