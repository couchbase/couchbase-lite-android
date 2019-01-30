//
// native_c4database.cc
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
#include <errno.h>
#include "c4.h"
#include "c4Document+Fleece.h"
#include "com_couchbase_litecore_C4Database.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Database
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    open
 * Signature: (Ljava/lang/String;ILjava/lang/String;II[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_open(JNIEnv *env, jclass clazz, jstring jpath,
                                            jint jflags,
                                            jstring storageEngine, jint versioning,
                                            jint encryptionAlg, jbyteArray encryptionKey) {
    jstringSlice path(env, jpath);

    C4DatabaseConfig config{};
    config.flags = (C4DatabaseFlags) jflags;
    config.storageEngine = kC4SQLiteStorageEngine;
    config.versioning = kC4RevisionTrees;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey))
        return 0;

    C4Error error;
    C4Database *db = c4db_open(path, &config, &error);
    if (!db)
        throwError(env, error);
    return (jlong) db;
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    copy
 * Signature: (Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;II[B)Z
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_copy(JNIEnv *env, jclass clazz,
                                            jstring jFromPath, jstring jToPath,
                                            jint jflags,
                                            jstring storageEngine, jint versioning,
                                            jint encryptionAlg, jbyteArray encryptionKey) {
    jstringSlice fromPath(env, jFromPath);
    jstringSlice toPath(env, jToPath);
    C4DatabaseConfig config{};
    config.flags = (C4DatabaseFlags) jflags;
    config.storageEngine = kC4SQLiteStorageEngine;
    config.versioning = kC4RevisionTrees;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey))
        return;

    C4Error error;
    if (!c4db_copy(fromPath, toPath, &config, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_free(JNIEnv *env, jclass clazz, jlong jdb) {
    c4db_free((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_close(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error;
    if (!c4db_close((C4Database *) jdb, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    delete
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_delete(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error;
    if (!c4db_delete((C4Database *) jdb, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    deleteAtPath
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_deleteAtPath(JNIEnv *env, jclass clazz, jstring jpath) {
    jstringSlice path(env, jpath);
    C4Error error;
    if (!c4db_deleteAtPath(path, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    rekey
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_rekey(JNIEnv *env, jclass clazz, jlong jdb,
                                             jint encryptionAlg, jbyteArray encryptionKey) {
    C4EncryptionKey key;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &key))
        return;

    C4Error error;
    if (!c4db_rekey((C4Database *) jdb, &key, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getPath
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Database_getPath(JNIEnv *env, jclass clazz, jlong jdb) {
    C4SliceResult slice = c4db_getPath((C4Database *) jdb);
    jstring ret = toJString(env, slice);
    c4slice_free(slice);
    return ret;
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getConfig
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_getConfig(JNIEnv *env, jclass clazz, jlong jdb) {
    return (jlong) c4db_getConfig((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_getDocumentCount(JNIEnv *env, jclass clazz, jlong jdb) {
    return (jlong) c4db_getDocumentCount((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getLastSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_getLastSequence(JNIEnv *env, jclass clazz, jlong jdb) {
    return (jlong) c4db_getLastSequence((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    nextDocExpiration
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_nextDocExpiration(JNIEnv *env, jclass clazz, jlong jdb) {
    return (jlong) c4db_nextDocExpiration((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    purgeExpiredDocs
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Database_purgeExpiredDocs(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error;
    int num = c4db_purgeExpiredDocs((C4Database *)jdb, &error);
    if (num == -1)
        throwError(env, error);
    return num;
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_purgeDoc(JNIEnv *env, jclass clazz,
                                                jlong jdb, jstring jdocID) {
    jstringSlice docID(env, jdocID);
    C4Error error;
    if (!c4db_purgeDoc((C4Database *)jdb, docID, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getMaxRevTreeDepth
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Database_getMaxRevTreeDepth(JNIEnv *env, jclass clazz, jlong jdb) {
    return (jint) c4db_getMaxRevTreeDepth((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    setMaxRevTreeDepth
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_setMaxRevTreeDepth(JNIEnv *env, jclass clazz, jlong jdb,
                                                          jint jmaxRevTreeDepth) {
    c4db_setMaxRevTreeDepth((C4Database *) jdb, jmaxRevTreeDepth);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getPublicUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4Database_getPublicUUID(JNIEnv *env, jclass clazz, jlong jdb) {
    C4UUID uuid;
    C4Error error;
    if (!c4db_getUUIDs((C4Database *) jdb, &uuid, nullptr, &error))
        throwError(env, error);
    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getPrivateUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4Database_getPrivateUUID(JNIEnv *env, jclass clazz, jlong jdb) {
    C4UUID uuid;
    C4Error error;
    if (!c4db_getUUIDs((C4Database *) jdb, nullptr, &uuid, &error))
        throwError(env, error);
    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    compact
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_compact(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error;
    if (!c4db_compact((C4Database *) jdb, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    beginTransaction
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_beginTransaction(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error;
    if (!c4db_beginTransaction((C4Database *) jdb, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    endTransaction
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_endTransaction(JNIEnv *env, jclass clazz, jlong jdb,
                                                      jboolean jcommit) {
    C4Error error;
    if (!c4db_endTransaction((C4Database *) jdb, jcommit, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    isInTransaction
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Database_isInTransaction(JNIEnv *env, jclass clazz, jlong jdb) {
    return c4db_isInTransaction((C4Database *) jdb);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    rawFree
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_rawFree(JNIEnv *env, jclass clazz, jlong jrawDoc) {
    c4raw_free((C4RawDocument *) jrawDoc);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    rawGet
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Database_rawGet(JNIEnv *env, jclass clazz, jlong jdb,
                                              jstring jstoreName, jstring jdocID) {
    jstringSlice storeName(env, jstoreName);
    jstringSlice docID(env, jdocID);
    C4Error error;
    C4RawDocument *rawDoc = c4raw_get((C4Database *) jdb, storeName, docID, &error);
    if (rawDoc == nullptr)
        throwError(env, error);
    return (jlong) rawDoc;
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    rawPut
 * Signature: (JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Database_rawPut(JNIEnv *env, jclass clazz, jlong jdb,
                                              jstring jstoreName,
                                              jstring jkey, jstring jmeta, jbyteArray jbody) {
    jstringSlice storeName(env, jstoreName);
    jstringSlice key(env, jkey);
    jstringSlice meta(env, jmeta);
    jbyteArraySlice body(env, jbody, false);
    C4Error error;
    if (!c4raw_put((C4Database *) jdb, storeName, key, meta, body, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getSharedFleeceEncoder
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Database_getSharedFleeceEncoder
        (JNIEnv *env, jclass clazz, jlong db) {
    return (jlong) c4db_getSharedFleeceEncoder((C4Database *) db);
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    encodeJSON
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Database_encodeJSON
        (JNIEnv *env, jclass clazz, jlong db,
         jbyteArray jbody) {
    jbyteArraySlice body(env, jbody, false);
    C4Error error = {};
    C4SliceResult res = c4db_encodeJSON((C4Database *) db, (C4Slice) body, &error);
    if (error.domain != 0 && error.code != 0)
        throwError(env, error);
    C4SliceResult *sliceResult = (C4SliceResult *) ::malloc(sizeof(C4SliceResult));
    sliceResult->buf = res.buf;
    sliceResult->size = res.size;
    return (jlong) sliceResult;
}

/*
 * Class:     com_couchbase_litecore_C4Database
 * Method:    getFLSharedKeys
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4Database_getFLSharedKeys
        (JNIEnv *env, jclass clazz, jlong db) {
    return (jlong) c4db_getFLSharedKeys((C4Database *) db);
}
