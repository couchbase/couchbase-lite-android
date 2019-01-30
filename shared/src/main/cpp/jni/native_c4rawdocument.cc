//
// native_c4rawdocument.cc
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
#include "com_couchbase_litecore_C4RawDocument.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4RawDocument
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4RawDocument
 * Method:    key
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4RawDocument_key(JNIEnv *env, jclass clazz, jlong jrawDoc) {
    return toJString(env, ((C4RawDocument *) jrawDoc)->key);
}

/*
 * Class:     com_couchbase_litecore_C4RawDocument
 * Method:    meta
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4RawDocument_meta(JNIEnv *env, jclass clazz, jlong jrawDoc) {
    return toJString(env, ((C4RawDocument *) jrawDoc)->meta);
}

/*
 * Class:     com_couchbase_litecore_C4RawDocument
 * Method:    body
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4RawDocument_body(JNIEnv *env, jclass clazz, jlong jrawDoc) {
    return toJByteArray(env, ((C4RawDocument *) jrawDoc)->body);
}