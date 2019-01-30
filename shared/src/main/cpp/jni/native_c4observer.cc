//
// native_c4observer.cc
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
#include "com_couchbase_litecore_C4DatabaseObserver.h"
#include "com_couchbase_litecore_C4DocumentObserver.h"
#include "native_glue.hh"
#include "logging.h"

using namespace litecore;
using namespace litecore::jni;

// -------------------------------------------------------------------------------------------------
// com_couchbase_litecore_C4DatabaseObserver and com_couchbase_litecore_C4DocumentObserver
// -------------------------------------------------------------------------------------------------

// C4DatabaseObserver
static jclass cls_C4DBObs;           // global reference
static jmethodID m_C4DBObs_callback; // callback method

// C4DatabaseChange
static jclass cls_C4DBChange; // global reference
static jmethodID m_C4DBChange_init;
static jfieldID f_C4DBChange_docID;
static jfieldID f_C4DBChange_revID;
static jfieldID f_C4DBChange_sequence;
static jfieldID f_C4DBChange_bodySize;
static jfieldID f_C4DBChange_external;

// C4DocumentObserver
static jclass cls_C4DocObs;           // global reference
static jmethodID m_C4DocObs_callback; // callback method

bool litecore::jni::initC4Observer(JNIEnv *env) {
    // Find `C4DatabaseObserver` class and `callback(long)` static method for callback
    {
        jclass localClass = env->FindClass("com/couchbase/litecore/C4DatabaseObserver");
        if (!localClass)
            return false;

        cls_C4DBObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DBObs)
            return false;

        m_C4DBObs_callback = env->GetStaticMethodID(cls_C4DBObs, "callback", "(J)V");
        if (!m_C4DBObs_callback)
            return false;
    }

    // Find `C4DocumentObserver.callback()` method id for callback
    {
        jclass localClass = env->FindClass("com/couchbase/litecore/C4DocumentObserver");
        if (!localClass)
            return false;

        cls_C4DocObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocObs)
            return false;

        m_C4DocObs_callback = env->GetStaticMethodID(cls_C4DocObs, "callback",
                                                     "(JLjava/lang/String;J)V");
        if (!m_C4DocObs_callback)
            return false;
    }

    // C4DatabaseChange, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/litecore/C4DatabaseChange");
        if (!localClass)
            return false;

        cls_C4DBChange = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DBChange)
            return false;

        m_C4DBChange_init = env->GetMethodID(cls_C4DBChange, "<init>", "()V");
        if (!m_C4DBChange_init)
            return false;

        f_C4DBChange_docID = env->GetFieldID(cls_C4DBChange, "docID", "Ljava/lang/String;");
        if (!f_C4DBChange_docID)
            return false;

        f_C4DBChange_revID = env->GetFieldID(cls_C4DBChange, "revID", "Ljava/lang/String;");
        if (!f_C4DBChange_revID)
            return false;

        f_C4DBChange_sequence = env->GetFieldID(cls_C4DBChange, "sequence", "J");
        if (!f_C4DBChange_sequence)
            return false;

        f_C4DBChange_bodySize = env->GetFieldID(cls_C4DBChange, "bodySize", "J");
        if (!f_C4DBChange_bodySize)
            return false;
        f_C4DBChange_external = env->GetFieldID(cls_C4DBChange, "external", "Z");
        if (!f_C4DBChange_external)
            return false;
    }
    return true;
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4DatabaseObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4DatabaseObserver
 * @param obs
 * @param ctx
 */
static void c4DBObsCallback(C4DatabaseObserver *obs, void *ctx) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4DBObs, m_C4DBObs_callback, (jlong) obs);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4DBObs, m_C4DBObs_callback, (jlong) obs);
            gJVM->DetachCurrentThread();
        }
    }
}

/*
 * Class:     com_couchbase_litecore_C4DatabaseObserver
 * Method:    create
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4DatabaseObserver_create(JNIEnv *, jclass, jlong db) {
    return (jlong) c4dbobs_create((C4Database *) db, c4DBObsCallback, (void *) NULL);
}

/*
 * Class:     com_couchbase_litecore_C4DatabaseObserver
 * Method:    getChanges
 * Signature: (JI)[Lcom/couchbase/litecore/C4DatabaseChange;
 */
JNIEXPORT jobjectArray JNICALL
Java_com_couchbase_litecore_C4DatabaseObserver_getChanges(JNIEnv *env,
                                                          jclass clazz,
                                                          jlong observer,
                                                          jint maxChanges) {
    //static const uint32_t kMaxChanges = 100u;
    C4DatabaseChange c4changes[maxChanges];
    bool external = false;
    uint32_t nChanges = c4dbobs_getChanges((C4DatabaseObserver *) observer,
                                           c4changes,
                                           maxChanges,
                                           &external);

    jobjectArray array = env->NewObjectArray(nChanges, cls_C4DBChange, NULL);
    for (size_t i = 0; i < nChanges; i++) {
        jobject obj = env->NewObject(cls_C4DBChange,
                                     m_C4DBChange_init);
        env->SetObjectField(obj, f_C4DBChange_docID,
                            toJString(env, c4changes[i].docID));
        env->SetObjectField(obj, f_C4DBChange_revID,
                            toJString(env, c4changes[i].revID));
        env->SetLongField(obj, f_C4DBChange_sequence, c4changes[i].sequence);
        env->SetLongField(obj, f_C4DBChange_bodySize, c4changes[i].bodySize);
        env->SetBooleanField(obj, f_C4DBChange_external, external);
        env->SetObjectArrayElement(array, i, obj);
    }
    c4dbobs_releaseChanges(c4changes, nChanges);
    return array;
}

/*
 * Class:     com_couchbase_litecore_C4DatabaseObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4DatabaseObserver_free(JNIEnv *env, jclass clazz, jlong observer) {
    if (observer != 0)
        c4dbobs_free((C4DatabaseObserver *) observer);
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4DocumentObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4DatabaseObserver
 * @param obs
 * @param ctx
 */
static void
c4DocObsCallback(C4DocumentObserver *obs, C4Slice docID, C4SequenceNumber seq, void *ctx) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4DocObs, m_C4DocObs_callback, (jlong) obs,
                                  toJString(env, docID), seq);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4DocObs, m_C4DocObs_callback, (jlong) obs,
                                      toJString(env, docID), seq);
            gJVM->DetachCurrentThread();
        }
    }
}

/*
 * Class:     com_couchbase_litecore_C4DocumentObserver
 * Method:    create
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_C4DocumentObserver_create
        (JNIEnv *env, jclass clazz, jlong jdb, jstring jdocID) {
    jstringSlice docID(env, jdocID);
    return (jlong) c4docobs_create((C4Database *) jdb, docID, c4DocObsCallback, NULL);
}

/*
 * Class:     com_couchbase_litecore_C4DocumentObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4DocumentObserver_free(JNIEnv *env, jclass clazz, jlong obs) {
    if (obs != 0)
        c4docobs_free((C4DocumentObserver *) obs);
}
