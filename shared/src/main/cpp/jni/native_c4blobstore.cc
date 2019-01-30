//
// native_c4blobstore.cc
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
#include <c4.h>
#include "com_couchbase_litecore_C4BlobKey.h"
#include "com_couchbase_litecore_C4BlobReadStream.h"
#include "com_couchbase_litecore_C4BlobStore.h"
#include "com_couchbase_litecore_C4BlobWriteStream.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4BlobKey
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    getBlobStore
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_getBlobStore(JNIEnv *env, jclass clazz, jlong jdb) {
    C4Error error = {};
    C4BlobStore *store = c4db_getBlobStore((C4Database *) jdb, &error);
    if (store == NULL)
        throwError(env, error);
    return (jlong) store;
}

/*
 * Class:     com_couchbase_litecore_C4BlobKey
 * Method:    fromString
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobKey_fromString(JNIEnv *env, jclass clazz, jstring jstr) {
    jstringSlice str(env, jstr);
    C4BlobKey *pBlobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    if (!c4blob_keyFromString(str, pBlobKey)) {
        free((C4BlobKey *) pBlobKey);
        throwError(env, {LiteCoreDomain, 0});
    }
    return (jlong) pBlobKey;
}

/*
 * Class:     com_couchbase_litecore_C4BlobKey
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4BlobKey_toString(JNIEnv *env, jclass clazz, jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    C4StringResult result = c4blob_keyToString(*pBlobKey);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_litecore_C4BlobKey
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobKey_free(JNIEnv *env, jclass clazz, jlong jblobkey) {
    free((C4BlobKey *) jblobkey);
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4BlobStore
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    openStore
 * Signature: (Ljava/lang/String;J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_openStore(JNIEnv *env, jclass clazz, jstring jdirpath,
                                                  jlong jflags) {
    jstringSlice dirPath(env, jdirpath);
    C4Error error;
    // TODO: Need to work for encryption
    C4BlobStore *store = c4blob_openStore(dirPath, (C4DatabaseFlags) jflags, NULL, &error);
    if (store == NULL)
        throwError(env, error);
    return (jlong) store;
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    deleteStore
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobStore_deleteStore(JNIEnv *env, jclass clazz, jlong jblobstore) {
    C4Error error;
    if (!c4blob_deleteStore((C4BlobStore *) jblobstore, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    freeStore
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobStore_freeStore(JNIEnv *env, jclass clazz, jlong jblobstore) {
    c4blob_freeStore((C4BlobStore *) jblobstore);
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    getSize
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_getSize(JNIEnv *env, jclass clazz, jlong jblobstore,
                                                jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    return (jlong) c4blob_getSize((C4BlobStore *) jblobstore, *pBlobKey);
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    getContents
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_getContents(JNIEnv *env, jclass clazz, jlong jblobstore,
                                                    jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error = {};
    C4SliceResult res = c4blob_getContents((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (error.domain != 0 && error.code != 0)
        throwError(env, error);
    C4SliceResult *sliceResult = (C4SliceResult *) ::malloc(sizeof(C4SliceResult));
    sliceResult->buf = res.buf;
    sliceResult->size = res.size;
    return (jlong) sliceResult;
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    getFilePath
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4BlobStore_getFilePath(JNIEnv *env, jclass clazz, jlong jblobstore,
                                                    jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error = {};
    C4StringResult res = c4blob_getFilePath((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (error.domain != 0 && error.code != 0)
        throwError(env, error);
    jstring ret = toJString(env, res);
    c4slice_free(res);
    return ret;
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    create
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_create(JNIEnv *env, jclass clazz, jlong jblobstore,
                                               jbyteArray jcontents) {
    jbyteArraySlice ccontents(env, jcontents, true);

    C4BlobKey blobKey;
    C4Error error = {};
    if (!c4blob_create((C4BlobStore *) jblobstore, ccontents, nullptr, &blobKey, &error)) {
        throwError(env, error);
    }

    C4BlobKey *pBlobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    *pBlobKey = blobKey;
    return (jlong) pBlobKey;
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    delete
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobStore_delete(JNIEnv *env, jclass clazz, jlong jblobstore,
                                               jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error = {};
    if (!c4blob_delete((C4BlobStore *) jblobstore, *pBlobKey, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    openReadStream
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_openReadStream(JNIEnv *env, jclass clazz, jlong jblobstore,
                                                       jlong jblobkey) {
    C4BlobKey *pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error = {};
    C4ReadStream *stream = c4blob_openReadStream((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (stream == NULL)
        throwError(env, error);
    return (jlong) stream;
}

/*
 * Class:     com_couchbase_litecore_C4BlobStore
 * Method:    openWriteStream
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobStore_openWriteStream(JNIEnv *env, jclass clazz,
                                                        jlong jblobstore) {
    C4Error error = {};
    C4WriteStream *stream = c4blob_openWriteStream((C4BlobStore *) jblobstore, &error);
    if (stream == NULL)
        throwError(env, error);
    return (jlong) stream;
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4BlobReadStream
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4BlobReadStream
 * Method:    read
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4BlobReadStream_read(JNIEnv *env, jclass clazz, jlong jstream,
                                                  jlong jsize) {

    C4Error error = {};
    char buff[(size_t) jsize];
    size_t read = c4stream_read((C4ReadStream *) jstream,
                                buff,
                                (size_t) jsize,
                                &error);
    C4Slice s = {buff, read};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_litecore_C4BlobReadStream
 * Method:    getLength
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobReadStream_getLength(JNIEnv *env, jclass clazz, jlong jstream) {
    C4Error error = {};
    int64_t length = c4stream_getLength((C4ReadStream *) jstream, &error);
    if (length == -1)
        throwError(env, error);
    return (jlong) length;
}

/*
 * Class:     com_couchbase_litecore_C4BlobReadStream
 * Method:    seek
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobReadStream_seek(JNIEnv *env, jclass clazz, jlong jstream,
                                                  jlong jposition) {
    C4Error error = {};
    if (!c4stream_seek((C4ReadStream *) jstream, (uint64_t) jposition, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4BlobReadStream
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobReadStream_close(JNIEnv *env, jclass clazz, jlong jstream) {
    c4stream_close((C4ReadStream *) jstream);
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4BlobWriteStream
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4BlobWriteStream
 * Method:    write
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobWriteStream_write(JNIEnv *env, jclass clazz, jlong jstream,
                                                    jbyteArray jbytes) {
    jbyteArraySlice bytes(env, jbytes, true);
    C4Slice slice = (C4Slice) bytes;
    C4Error error = {};
    if (!c4stream_write((C4WriteStream *) jstream, slice.buf, slice.size, &error))
        throwError(env, error);

}

/*
 * Class:     com_couchbase_litecore_C4BlobWriteStream
 * Method:    computeBlobKey
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4BlobWriteStream_computeBlobKey(JNIEnv *env, jclass clazz,
                                                             jlong jstream) {
    C4BlobKey *blobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    *blobKey = c4stream_computeBlobKey((C4WriteStream *) jstream);
    return (jlong) blobKey;
}

/*
 * Class:     com_couchbase_litecore_C4BlobWriteStream
 * Method:    install
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobWriteStream_install(JNIEnv *env, jclass clazz, jlong jstream) {
    C4Error error = {};
    if (!c4stream_install((C4WriteStream *) jstream, nullptr, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4BlobWriteStream
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4BlobWriteStream_close(JNIEnv *env, jclass clazz, jlong jstream) {
    c4stream_closeWriter((C4WriteStream *) jstream);
}