//
// C4Document.java
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

import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLSharedKeys;
import com.couchbase.litecore.fleece.FLSliceResult;

public class C4Document extends RefCounted implements C4Constants {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long _handle = 0L; // hold pointer to C4Document

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4Document(long db, String docID, boolean mustExist) throws LiteCoreException {
        this(get(db, docID, mustExist));
    }

    C4Document(long db, long sequence) throws LiteCoreException {
        this(getBySequence(db, sequence));
    }

    C4Document(long handle) {
        if (handle == 0)
            throw new IllegalArgumentException("handle is 0");
        this._handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    @Override
    void free() {
        if (_handle != 0L) {
            free(_handle);
            _handle = 0L;
        }
    }

    // - C4Document
    public int getFlags() {
        return getFlags(_handle);
    }

    public String getDocID() {
        return getDocID(_handle);
    }

    public String getRevID() {
        return getRevID(_handle);
    }

    public long getSequence() {
        return getSequence(_handle);
    }

    // - C4Revision

    public String getSelectedRevID() {
        return getSelectedRevID(_handle);
    }

    public int getSelectedFlags() {
        return getSelectedFlags(_handle);
    }

    public long getSelectedSequence() {
        return getSelectedSequence(_handle);
    }

    public byte[] getSelectedBody() {
        return getSelectedBody(_handle);
    }

    public FLDict getSelectedBody2() {
        long value = getSelectedBody2(_handle);
        return value == 0 ? null : new FLDict(value);
    }

    // - Lifecycle

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        save(_handle, maxRevTreeDepth);
    }

    // - Revisions

    public boolean selectCurrentRevision() {
        return selectCurrentRevision(_handle);
    }

    public void loadRevisionBody() throws LiteCoreException {
        loadRevisionBody(_handle);
    }

    public boolean hasRevisionBody() {
        return hasRevisionBody(_handle);
    }

    public boolean selectParentRevision() {
        return selectParentRevision(_handle);
    }

    public boolean selectNextRevision() {
        return selectNextRevision(_handle);
    }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody)
            throws LiteCoreException {
        selectNextLeafRevision(_handle, includeDeleted, withBody);
    }

    public boolean selectFirstPossibleAncestorOf(String revID) throws LiteCoreException {
        return selectFirstPossibleAncestorOf(_handle, revID);
    }

    public boolean selectNextPossibleAncestorOf(String revID) {
        return selectNextPossibleAncestorOf(_handle, revID);
    }

    public boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return selectCommonAncestorRevision(_handle, revID1, revID2);
    }

    public int purgeRevision(String revID) throws LiteCoreException {
        return purgeRevision(_handle, revID);
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
            throws LiteCoreException {
        resolveConflict(_handle, winningRevID, losingRevID, mergeBody, mergedFlags);
    }

    // - Creating and Updating Documents

    public C4Document update(byte[] body, int flags) throws LiteCoreException {
        return new C4Document(update(_handle, body, flags));
    }

    public C4Document update(FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(update2(_handle, body != null ? body.getHandle() : 0, flags));
    }

    //-------------------------------------------------------------------------
    // helper methods
    //-------------------------------------------------------------------------

    // helper methods for Document
    public boolean deleted() {
        return isSelectedRevFlags(C4RevisionFlags.kRevDeleted);
    }

    public boolean accessRemoved() {
        return isSelectedRevFlags(C4RevisionFlags.kRevPurged);
    }

    public boolean conflicted() {
        return isFlags(C4DocumentFlags.kDocConflicted);
    }

    public boolean exists() {
        return isFlags(C4DocumentFlags.kDocExists);
    }

    private boolean isFlags(int flag) {
        return (getFlags(_handle) & flag) == flag;
    }

    public boolean isSelectedRevFlags(int flag) {
        return (getSelectedFlags(_handle) & flag) == flag;
    }

    //-------------------------------------------------------------------------
    // Fleece-related
    //-------------------------------------------------------------------------

    public static boolean dictContainsBlobs(FLSliceResult dict, FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }

    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return bodyAsJSON(_handle, canonical);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // - C4Document
    static native int getFlags(long doc);

    static native String getDocID(long doc);

    static native String getRevID(long doc);

    static native long getSequence(long doc);

    // - C4Revision

    static native String getSelectedRevID(long doc);

    static native int getSelectedFlags(long doc);

    static native long getSelectedSequence(long doc);

    static native byte[] getSelectedBody(long doc);

    // return pointer to FLValue
    static native long getSelectedBody2(long doc);

    // - Lifecycle

    static native long get(long db, String docID, boolean mustExist)
            throws LiteCoreException;

    static native long getBySequence(long db, long sequence) throws LiteCoreException;

    static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    static native void free(long doc);

    // - Revisions

    static native boolean selectCurrentRevision(long doc);

    static native void loadRevisionBody(long doc) throws LiteCoreException;

    static native boolean hasRevisionBody(long doc);

    static native boolean selectParentRevision(long doc);

    static native boolean selectNextRevision(long doc);

    static native void selectNextLeafRevision(long doc, boolean includeDeleted,
                                              boolean withBody)
            throws LiteCoreException;

    static native boolean selectFirstPossibleAncestorOf(long doc, String revID);

    static native boolean selectNextPossibleAncestorOf(long doc, String revID);

    static native boolean selectCommonAncestorRevision(long doc,
                                                       String revID1, String revID2);

    static native int purgeRevision(long doc, String revID) throws LiteCoreException;

    static native void resolveConflict(long doc,
                                       String winningRevID, String losingRevID,
                                       byte[] mergeBody, int mergedFlags)
            throws LiteCoreException;

    // - Purging and Expiration

    static native void setExpiration(long db, String docID, long timestamp)
            throws LiteCoreException;

    static native long getExpiration(long db, String docID);

    // - Creating and Updating Documents

    static native long put(long db,
                           byte[] body,
                           String docID,
                           int revFlags,
                           boolean existingRevision,
                           boolean allowConflict,
                           String[] history,
                           boolean save,
                           int maxRevTreeDepth,
                           int remoteDBID)
            throws LiteCoreException;

    static native long put2(long db,
                            long body, // C4Slice*
                            String docID,
                            int revFlags,
                            boolean existingRevision,
                            boolean allowConflict,
                            String[] history,
                            boolean save,
                            int maxRevTreeDepth,
                            int remoteDBID)
            throws LiteCoreException;

    static native long create(long db, String docID, byte[] body, int flags) throws LiteCoreException;

    static native long create2(long db, String docID, long body, int flags) throws LiteCoreException;

    static native long update(long doc, byte[] body, int flags) throws LiteCoreException;

    static native long update2(long doc, long body, int flags) throws LiteCoreException;

    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // -- Fleece-related

    static native boolean dictContainsBlobs(long dict, long sk); // dict -> FLSliceResult

    static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;
    // doc -> pointer to C4Document
}
