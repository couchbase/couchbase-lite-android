//
// native_c4document.cc
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
#include <c4Document.h>
#include <c4.h>
#include <c4Document+Fleece.h>
#include <c4Base.h>
#include "com_couchbase_litecore_C4Document.h"
#include "native_glue.hh"
#include "fleece/Fleece.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Document
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getFlags
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Document_getFlags(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return doc->flags;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getDocID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Document_getDocID(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return toJString(env, doc->docID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Document_getRevID(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return toJString(env, doc->revID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_getSequence(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return doc->sequence;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSelectedRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Document_getSelectedRevID(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return toJString(env, doc->selectedRev.revID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSelectedFlags
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Document_getSelectedFlags(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return doc->selectedRev.flags;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSelectedSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_getSelectedSequence(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return doc->selectedRev.sequence;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSelectedBody
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4Document_getSelectedBody(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    return toJByteArray(env, doc->selectedRev.body);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getSelectedBody2
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_getSelectedBody2(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Document *doc = (C4Document *) jdoc;
    FLDict root = NULL;
    C4Slice body = doc->selectedRev.body;
    if (body.size > 0)
        root = FLValue_AsDict(FLValue_FromData({body.buf, body.size}, kFLTrusted));
    return (jlong) root;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    get
 * Signature: (JLjava/lang/String;Z)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_get(JNIEnv *env, jclass clazz, jlong jdb, jstring jdocID,
                                           jboolean mustExist) {
    jstringSlice docID(env, jdocID);

    C4Error error;
    C4Document *doc = c4doc_get((C4Database *) jdb, docID, mustExist, &error);
    if (doc == nullptr)
        throwError(env, error);
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getBySequence
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_getBySequence(JNIEnv *env, jclass clazz,
                                                     jlong jdb, jlong jsequence) {
    C4Error error;
    C4Document *doc = c4doc_getBySequence((C4Database *) jdb, jsequence, &error);
    if (doc == nullptr)
        throwError(env, error);
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    save
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Document_save(JNIEnv *env, jclass clazz,
                                            jlong jdoc, jint maxRevTreeDepth) {
    C4Error error;
    if (!c4doc_save((C4Document *) jdoc, maxRevTreeDepth, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Document_free(JNIEnv *env, jclass clazz, jlong jdoc) {
    c4doc_free((C4Document *) jdoc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectCurrentRevision
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectCurrentRevision(JNIEnv *env, jclass clazz,
                                                             jlong jdoc) {
    return c4doc_selectCurrentRevision((C4Document *) jdoc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    loadRevisionBody
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Document_loadRevisionBody(JNIEnv *env, jclass clazz, jlong jdoc) {
    C4Error error;
    if (!c4doc_loadRevisionBody((C4Document *) jdoc, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    hasRevisionBody
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_hasRevisionBody(JNIEnv *env, jclass clazz, jlong jdoc) {
    return c4doc_hasRevisionBody((C4Document *) jdoc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectParentRevision
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectParentRevision(JNIEnv *env, jclass clazz, jlong jdoc) {
    return c4doc_selectParentRevision((C4Document *) jdoc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectNextRevision
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectNextRevision(JNIEnv *env, jclass clazz, jlong jdoc) {
    return c4doc_selectNextRevision((C4Document *) jdoc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectNextLeafRevision
 * Signature: (JZZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Document_selectNextLeafRevision(JNIEnv *env, jclass clazz, jlong jdoc,
                                                              jboolean jincludeDeleted,
                                                              jboolean jwithBody) {
    C4Error error;
    if (!c4doc_selectNextLeafRevision((C4Document *) jdoc, jincludeDeleted, jwithBody, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectFirstPossibleAncestorOf
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectFirstPossibleAncestorOf(JNIEnv *env, jclass clazz,
                                                                     jlong jdoc, jstring jrevID) {
    jstringSlice revID(env, jrevID);
    return c4doc_selectFirstPossibleAncestorOf((C4Document *) jdoc, revID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectNextPossibleAncestorOf
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectNextPossibleAncestorOf(JNIEnv *env, jclass clazz,
                                                                    jlong jdoc, jstring jrevID) {
    jstringSlice revID(env, jrevID);
    return c4doc_selectNextPossibleAncestorOf((C4Document *) jdoc, revID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    selectCommonAncestorRevision
 * Signature: (JLjava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_selectCommonAncestorRevision(JNIEnv *env, jclass clazz,
                                                                    jlong jdoc,
                                                                    jstring jRev1,
                                                                    jstring jRev2) {
    jstringSlice rev1(env, jRev1);
    jstringSlice rev2(env, jRev2);
    return c4doc_selectCommonAncestorRevision((C4Document *) jdoc, rev1, rev2);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    purgeRevision
 * Signature: (JLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Document_purgeRevision(JNIEnv *env, jclass clazz,
                                                     jlong jdoc,
                                                     jstring jrevID) {
    jstringSlice revID(env, jrevID);
    C4Error error;
    int num = c4doc_purgeRevision((C4Document *) jdoc, revID, &error);
    if (num == -1)
        throwError(env, error);
    return num;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    resolveConflict
 * Signature: (JLjava/lang/String;Ljava/lang/String;[BI)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_litecore_C4Document_resolveConflict
        (JNIEnv *env, jclass clazz, jlong jdoc,
         jstring jWinningRevID,
         jstring jLosingRevID,
         jbyteArray jMergedBody, jint jMergedFlags) {

    jstringSlice winningRevID(env, jWinningRevID);
    jstringSlice losingRevID(env, jLosingRevID);
    jbyteArraySlice mergedBody(env, jMergedBody, false);
    C4RevisionFlags revisionFlag = (C4RevisionFlags)jMergedFlags;
    C4Error error = {};
    if (!c4doc_resolveConflict((C4Document *) jdoc, winningRevID, losingRevID, mergedBody, revisionFlag,
                               &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    setExpiration
 * Signature: (JLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Document_setExpiration(JNIEnv *env, jclass clazz,
                                                     jlong jdb, jstring jdocID,
                                                     jlong jtimestamp) {
    jstringSlice docID(env, jdocID);
    C4Error error;
    if (!c4doc_setExpiration((C4Database *)jdb, docID, jtimestamp, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    getExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_getExpiration(JNIEnv *env, jclass clazz,
                                                     jlong jdb, jstring jdocID) {
    jstringSlice docID(env, jdocID);
    return c4doc_getExpiration((C4Database *) jdb, docID);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    put
 * Signature: (J[BLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_put(JNIEnv *env, jclass clazz,
                                           jlong jdb,
                                           jbyteArray jbody,
                                           jstring jdocID,
                                           jint revFlags,
                                           jboolean existingRevision,
                                           jboolean allowConflict,
                                           jobjectArray jhistory,
                                           jboolean save,
                                           jint maxRevTreeDepth,
                                           jint remoteDBID) {

    C4Database *db = (C4Database *) jdb;
    jstringSlice docID(env, jdocID);
    jbyteArraySlice body(env, jbody, false);

    C4DocPutRequest rq = {};
    rq.body = body;                         ///< Revision's body
    rq.docID = docID;                       ///< Document ID
    rq.revFlags = revFlags;                 ///< Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision; ///< Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;       ///< OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                   ///< Array of ancestor revision IDs
    rq.historyCount = 0;                    ///< Size of history[] array
    rq.save = save;                         ///< Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;   ///< Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID)remoteDBID; ///< Identifier of remote db this rev's from (or 0 if local)

    // history
    // Convert jhistory, a Java String[], to a C array of C4Slice:
    jsize n = env->GetArrayLength(jhistory);
    if (env->EnsureLocalCapacity(std::min(n + 1, MaxLocalRefsToUse)) < 0)
        return -1;
    std::vector<C4Slice> history(n);
    std::vector<jstringSlice *> historyAlloc;
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring) env->GetObjectArrayElement(jhistory, i);
        jstringSlice *item = new jstringSlice(env, js);
        if (i >= MaxLocalRefsToUse)
            item->copyAndReleaseRef();
        historyAlloc.push_back(item); // so its memory won't be freed
        history[i] = *item;
    }
    rq.history = history.data();
    rq.historyCount = history.size();

    size_t commonAncestorIndex;
    C4Error error;
    C4Document *doc = c4doc_put(db, &rq, &commonAncestorIndex, &error);

    // release memory
    for (jsize i = 0; i < n; i++)
        delete historyAlloc.at(i);

    if (!doc)
        throwError(env, error);

    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    put2
 * Signature: (JJLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Document_put2(JNIEnv *env, jclass clazz,
                                                                    jlong jdb,
                                                                    jlong jbody,
                                                                    jstring jdocID,
                                                                    jint revFlags,
                                                                    jboolean existingRevision,
                                                                    jboolean allowConflict,
                                                                    jobjectArray jhistory,
                                                                    jboolean save,
                                                                    jint maxRevTreeDepth,
                                                                    jint remoteDBID) {
    C4Database *db = (C4Database *) jdb;
    C4Slice *pBody = (C4Slice *) jbody;
    jstringSlice docID(env, jdocID);

    // Parameters for adding a revision using c4doc_put.
    C4DocPutRequest rq = {};
    rq.body = *pBody;       ///< Revision's body
    rq.docID = docID;       ///< Document ID
    rq.revFlags = revFlags; ///< Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision; ///< Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;       ///< OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                   ///< Array of ancestor revision IDs
    rq.historyCount = 0;                    ///< Size of history[] array
    rq.save = save;                         ///< Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;   ///< Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID)remoteDBID; ///< Identifier of remote db this rev's from (or 0 if local)

    // history
    // Convert jhistory, a Java String[], to a C array of C4Slice:
    jsize n = env->GetArrayLength(jhistory);
    if (env->EnsureLocalCapacity(std::min(n + 1, MaxLocalRefsToUse)) < 0)
        return -1;
    std::vector<C4Slice> history(n);
    std::vector<jstringSlice *> historyAlloc;
    if (n > 0) {
        for (jsize i = 0; i < n; i++) {
            jstring js = (jstring) env->GetObjectArrayElement(jhistory, i);
            jstringSlice *item = new jstringSlice(env, js);
            if (i >= MaxLocalRefsToUse)
                item->copyAndReleaseRef();
            historyAlloc.push_back(item); // so its memory won't be freed
            history[i] = *item;
        }
        rq.history = history.data();
        rq.historyCount = history.size();
    }

    size_t commonAncestorIndex;
    C4Error error;
    C4Document *doc = c4doc_put(db, &rq, &commonAncestorIndex, &error);

    // release memory
    for (jsize i = 0; i < n; i++)
        delete historyAlloc.at(i);

    if (!doc)
        throwError(env, error);

    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    create
 * Signature: (JLjava/lang/String;[BI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_create(JNIEnv *env, jclass clazz,
                                              jlong jdb, jstring jdocID,
                                              jbyteArray jbody, jint flags) {
    jstringSlice docID(env, jdocID);
    jbyteArraySlice body(env, jbody, false);
    C4Error error;
    C4Document *doc = c4doc_create((C4Database *) jdb, docID, body, flags, &error);
    if (!doc)
        throwError(env, error);
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    create2
 * Signature: (JLjava/lang/String;JI)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Document_create2(JNIEnv *env, jclass clazz,
                                                                       jlong jdb, jstring jdocID,
                                                                       jlong jbody, jint flags) {
    C4Slice body;
    if (jbody != 0)
        body = *(C4Slice *) jbody;
    else
        body = kC4SliceNull;
    jstringSlice docID(env, jdocID);
    C4Error error;
    C4Document *doc = c4doc_create((C4Database *) jdb, docID, body, flags, &error);
    if (!doc)
        throwError(env, error);
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    update
 * Signature: (J[BI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Document_update(JNIEnv *env, jclass clazz,
                                              jlong jdoc,
                                              jbyteArray jbody, jint flags) {
    jbyteArraySlice body(env, jbody, false);
    C4Error error;
    C4Document *doc = c4doc_update((C4Document *) jdoc, body, flags, &error);
    if (!doc)
        throwError(env, error);
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    update2
 * Signature: (JJI)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Document_update2(JNIEnv *env, jclass clazz,
                                                                       jlong jdoc,
                                                                       jlong jbody, jint flags) {
    C4Document *doc = (C4Document *) jdoc;
    if (doc == NULL)
        throwError(env, {LiteCoreDomain, kC4ErrorAssertionFailed});

    C4Slice body;
    if (jbody != 0)
        body = *(C4Slice *) jbody;
    else
        body = kC4SliceNull;
    C4Error error;
    C4Document *newDoc = c4doc_update((C4Document *) jdoc, body, flags, &error);
    if (!newDoc)
        throwError(env, error);
    return (jlong) newDoc;
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    dictContainsBlobs
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Document_dictContainsBlobs(JNIEnv *env, jclass clazz,
                                                          jlong jbody, jlong jsk) {
    Doc doc(*(alloc_slice *) jbody, kFLTrusted, (FLSharedKeys) jsk);
    return c4doc_dictContainsBlobs(doc);
}

/*
 * Class:     com_couchbase_litecore_C4Document
 * Method:    bodyAsJSON
 * Signature: (JZ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Document_bodyAsJSON(JNIEnv *env, jclass clazz, jlong jdoc,
                                                  jboolean canonical) {
    C4Error error = {};
    C4StringResult result = c4doc_bodyAsJSON((C4Document *) jdoc, canonical, &error);
    if (error.code != 0)
        throwError(env, error);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}