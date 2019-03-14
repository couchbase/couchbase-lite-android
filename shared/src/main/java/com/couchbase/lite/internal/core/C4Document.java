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
package com.couchbase.lite.internal.core;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;


public class C4Document extends RefCounted implements C4Constants {
    public static boolean dictContainsBlobs(FLSliceResult dict, FLSharedKeys sk) {
        return dictContainsBlobs(dict.getHandle(), sk.getHandle());
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    // - C4Document
    static native int getFlags(long doc);

    static native String getDocID(long doc);

    static native String getRevID(long doc);

    static native long getSequence(long doc);

    static native String getSelectedRevID(long doc);

    static native int getSelectedFlags(long doc);

    static native long getSelectedSequence(long doc);

    static native byte[] getSelectedBody(long doc);

    // - C4Revision

    // return pointer to FLValue
    static native long getSelectedBody2(long doc);

    static native long get(long db, String docID, boolean mustExist)
        throws LiteCoreException;

    static native long getBySequence(long db, long sequence) throws LiteCoreException;

    static native void save(long doc, int maxRevTreeDepth) throws LiteCoreException;

    static native void free(long doc);

    // - Lifecycle

    static native boolean selectCurrentRevision(long doc);

    // - Revisions

    static native void loadRevisionBody(long doc) throws LiteCoreException;

    static native boolean hasRevisionBody(long doc);

    static native boolean selectParentRevision(long doc);

    static native boolean selectNextRevision(long doc);

    static native void selectNextLeafRevision(
        long doc, boolean includeDeleted,
        boolean withBody)
        throws LiteCoreException;

    static native boolean selectFirstPossibleAncestorOf(long doc, String revID);

    static native boolean selectNextPossibleAncestorOf(long doc, String revID);

    static native boolean selectCommonAncestorRevision(
        long doc,
        String revID1, String revID2);

    static native int purgeRevision(long doc, String revID) throws LiteCoreException;

    static native void resolveConflict(
        long doc,
        String winningRevID, String losingRevID,
        byte[] mergeBody, int mergedFlags)
        throws LiteCoreException;

    static native void setExpiration(long db, String docID, long timestamp)
        throws LiteCoreException;

    // - Creating and Updating Documents

    static native long getExpiration(long db, String docID);

    static native long put(
        long db,
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

    //-------------------------------------------------------------------------
    // helper methods
    //-------------------------------------------------------------------------

    static native long put2(
        long db,
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

    static native boolean dictContainsBlobs(long dict, long sk); // dict -> FLSliceResult

    //-------------------------------------------------------------------------
    // Fleece-related
    //-------------------------------------------------------------------------

    static native String bodyAsJSON(long doc, boolean canonical) throws LiteCoreException;
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle; // hold pointer to C4Document

    C4Document(long db, String docID, boolean mustExist) throws LiteCoreException {
        this(get(db, docID, mustExist));
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    C4Document(long db, long sequence) throws LiteCoreException {
        this(getBySequence(db, sequence));
    }

    C4Document(long handle) {
        if (handle == 0) { throw new IllegalArgumentException("handle is 0"); }
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    @Override
    void free() {
        if (handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    // - C4Document
    public int getFlags() {
        return getFlags(handle);
    }

    // - C4Revision

    public String getDocID() {
        return getDocID(handle);
    }

    public String getRevID() {
        return getRevID(handle);
    }

    public long getSequence() {
        return getSequence(handle);
    }

    public String getSelectedRevID() {
        return getSelectedRevID(handle);
    }

    public int getSelectedFlags() {
        return getSelectedFlags(handle);
    }

    // - Lifecycle

    public long getSelectedSequence() {
        return getSelectedSequence(handle);
    }

    public byte[] getSelectedBody() {
        return getSelectedBody(handle);
    }

    public FLDict getSelectedBody2() {
        final long value = getSelectedBody2(handle);
        return value == 0 ? null : new FLDict(value);
    }

    public void save(int maxRevTreeDepth) throws LiteCoreException {
        save(handle, maxRevTreeDepth);
    }

    // - Revisions

    public boolean selectCurrentRevision() {
        return selectCurrentRevision(handle);
    }

    public void loadRevisionBody() throws LiteCoreException {
        loadRevisionBody(handle);
    }

    public boolean hasRevisionBody() {
        return hasRevisionBody(handle);
    }

    public boolean selectParentRevision() {
        return selectParentRevision(handle);
    }

    public boolean selectNextRevision() {
        return selectNextRevision(handle);
    }

    public void selectNextLeafRevision(boolean includeDeleted, boolean withBody)
        throws LiteCoreException {
        selectNextLeafRevision(handle, includeDeleted, withBody);
    }

    public boolean selectFirstPossibleAncestorOf(String revID) throws LiteCoreException {
        return selectFirstPossibleAncestorOf(handle, revID);
    }

    public boolean selectNextPossibleAncestorOf(String revID) {
        return selectNextPossibleAncestorOf(handle, revID);
    }

    public boolean selectCommonAncestorRevision(String revID1, String revID2) {
        return selectCommonAncestorRevision(handle, revID1, revID2);
    }

    public int purgeRevision(String revID) throws LiteCoreException {
        return purgeRevision(handle, revID);
    }

    public void resolveConflict(String winningRevID, String losingRevID, byte[] mergeBody, int mergedFlags)
        throws LiteCoreException {
        resolveConflict(handle, winningRevID, losingRevID, mergeBody, mergedFlags);
    }

    // - Purging and Expiration

    public C4Document update(byte[] body, int flags) throws LiteCoreException {
        return new C4Document(update(handle, body, flags));
    }

    public C4Document update(FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(update2(handle, body != null ? body.getHandle() : 0, flags));
    }

    // - Creating and Updating Documents

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
        return (getFlags(handle) & flag) == flag;
    }

    public boolean isSelectedRevFlags(int flag) {
        return (getSelectedFlags(handle) & flag) == flag;
    }

    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // -- Fleece-related

    public String bodyAsJSON(boolean canonical) throws LiteCoreException {
        return bodyAsJSON(handle, canonical);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }
    // doc -> pointer to C4Document
}
