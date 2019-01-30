/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
#include <c4Listener.h>
#include "com_couchbase_litecore_C4Listener.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Listener
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    availableAPIs
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Listener_availableAPIs(JNIEnv *env, jclass clazz) {
    return (jint) c4listener_availableAPIs();
}

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    start
 * Signature: (IILjava/lang/String;ZZZZ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Listener_start(JNIEnv *env, jclass clazz, jint port, jint apis,
                                             jstring jdirectory, jboolean allowCreateDBs,
                                             jboolean allowDeleteDBs, jboolean allowPush,
                                             jboolean allowPull) {
    jstringSlice directory(env, jdirectory);
    C4ListenerConfig config = {
            .port = (uint16_t)port,
            .apis = (C4ListenerAPIs)apis,
            .directory = {((slice) directory).buf, ((slice) directory).size},
            .allowCreateDBs = (bool)allowCreateDBs,
            .allowDeleteDBs = (bool)allowDeleteDBs,
            .allowPush = (bool)allowPush,
            .allowPull = (bool)allowPull
    };
    C4Error error = {};
    C4Listener *listener = c4listener_start(&config, &error);
    if (!listener)
        throwError(env, error);
    return (jlong) listener;
}

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Listener_free(JNIEnv *env, jclass clazz, jlong listener) {
    if (!listener) return;

    c4listener_free((C4Listener *) listener);
}

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    shareDB
 * Signature: (JLjava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Listener_shareDB(JNIEnv *env, jclass clazz, jlong listener,
                                               jstring jname, jlong db) {
    if (!listener) return false;

    jstringSlice name(env, jname);
    return c4listener_shareDB((C4Listener *) listener, name, (C4Database *) db);
}

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    unshareDB
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Listener_unshareDB(JNIEnv *env, jclass clazz, jlong listener,
                                                 jstring jname) {
    if (!listener) return false;

    jstringSlice name(env, jname);
    return c4listener_unshareDB((C4Listener *) listener, name);
}

/*
 * Class:     com_couchbase_litecore_C4Listener
 * Method:    uriNameFromPath
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Listener_uriNameFromPath(JNIEnv *env, jclass clazz, jstring jpath) {
    jstringSlice path(env, jpath);
    C4StringResult result = c4db_URINameFromPath(path);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}