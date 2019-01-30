//
// native_allocslice.cc
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

#include "native_glue.hh"
#include "com_couchbase_litecore_fleece_AllocSlice.h"

using namespace fleece;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// AllocSlice
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_fleece_AllocSlice
 * Method:    init
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_fleece_AllocSlice_init(JNIEnv *env, jclass clazz, jbyteArray jvalue) {
    alloc_slice *ptr = new alloc_slice;
    if (jvalue != NULL)
        *ptr = jbyteArraySlice::copy(env, jvalue);
    return (jlong) ptr;
}

/*
 * Class:     com_couchbase_litecore_fleece_AllocSlice
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_fleece_AllocSlice_free(JNIEnv *env, jclass clazz, jlong jslice) {
    delete (alloc_slice *) jslice;
}

/*
 * Class:     com_couchbase_litecore_fleece_AllocSlice
 * Method:    getBuf
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_fleece_AllocSlice_getBuf(JNIEnv *env, jclass clazz, jlong jslice) {
    alloc_slice *s = (alloc_slice *) jslice;
    return toJByteArray(env, (C4Slice) {s->buf, s->size});
}

/*
 * Class:     com_couchbase_litecore_fleece_AllocSlice
 * Method:    getSize
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_fleece_AllocSlice_getSize(JNIEnv *env, jclass clazz, jlong jslice) {
    return (jlong) ((alloc_slice *) jslice)->size;
}
