//
// native_c4replicator.cc
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
#include <c4Replicator.h>
#include <c4Base.h>
#include <c4Socket.h>
#include "socket_factory.h"
#include "com_couchbase_lite_internal_core_C4Replicator.h"
#include "native_glue.hh"
#include "logging.h"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Replicator
// ----------------------------------------------------------------------------

// C4Replicator
static jclass cls_C4Replicator;                         // global reference
static jmethodID m_C4Replicator_statusChangedCallback;  // statusChangedCallback method
static jmethodID m_C4Replicator_documentEndedCallback;  // documentEndedCallback method
static jmethodID m_C4Replicator_validationFunction;     // validationFunction method

// C4ReplicatorStatus
static jclass cls_C4ReplStatus; // global reference
static jmethodID m_C4ReplStatus_init;
static jfieldID f_C4ReplStatus_activityLevel;
static jfieldID f_C4ReplStatus_progressUnitsCompleted;
static jfieldID f_C4ReplStatus_progressUnitsTotal;
static jfieldID f_C4ReplStatus_progressDocumentCount;
static jfieldID f_C4ReplStatus_errorDomain;
static jfieldID f_C4ReplStatus_errorCode;
static jfieldID f_C4ReplStatus_errorInternalInfo;

//C4DocumentEnded
static jclass cls_C4DocEnded;
static jmethodID m_C4DocEnded_init;
static jfieldID f_C4DocEnded_docID;
static jfieldID f_C4DocEnded_revID;
static jfieldID f_C4DocEnded_flags;
static jfieldID f_C4DocEnded_sequence;
static jfieldID f_C4DocEnded_errorIsTransient;
static jfieldID f_C4DocEnded_errorDomain;
static jfieldID f_C4DocEnded_errorCode;
static jfieldID f_C4DocEnded_errorInternalInfo;

bool litecore::jni::initC4Replicator(JNIEnv *env) {
    // Find `C4Replicator` class and `statusChangedCallback(long, C4ReplicatorStatus )` static method for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Replicator");
        if (!localClass)
            return false;

        cls_C4Replicator = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4Replicator)
            return false;

        m_C4Replicator_statusChangedCallback = env->GetStaticMethodID(cls_C4Replicator,
                                                                      "statusChangedCallback",
                                                                      "(JLcom/couchbase/lite/internal/core/C4ReplicatorStatus;)V");
        if (!m_C4Replicator_statusChangedCallback)
            return false;

        m_C4Replicator_documentEndedCallback = env->GetStaticMethodID(cls_C4Replicator,
                                                                      "documentEndedCallback",
                                                                      "(JZ[Lcom/couchbase/lite/internal/core/C4DocumentEnded;)V");

        if (!m_C4Replicator_documentEndedCallback)
            return false;

        m_C4Replicator_validationFunction = env->GetStaticMethodID(cls_C4Replicator,
                                                                      "validationFunction",
                                                                      "(Ljava/lang/String;IJZLjava/lang/Object;)Z");
        if (!m_C4Replicator_validationFunction)
            return false;
    }

    // C4ReplicatorStatus, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4ReplicatorStatus");
        if (!localClass)
            return false;

        cls_C4ReplStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4ReplStatus)
            return false;

        m_C4ReplStatus_init = env->GetMethodID(cls_C4ReplStatus, "<init>", "()V");
        if (!m_C4ReplStatus_init)
            return false;

        f_C4ReplStatus_activityLevel = env->GetFieldID(cls_C4ReplStatus, "activityLevel", "I");
        if (!f_C4ReplStatus_activityLevel)
            return false;

        f_C4ReplStatus_progressUnitsCompleted = env->GetFieldID(cls_C4ReplStatus,
                                                                "progressUnitsCompleted", "J");
        if (!f_C4ReplStatus_progressUnitsCompleted)
            return false;

        f_C4ReplStatus_progressUnitsTotal = env->GetFieldID(cls_C4ReplStatus, "progressUnitsTotal",
                                                            "J");
        if (!f_C4ReplStatus_progressUnitsTotal)
            return false;

        f_C4ReplStatus_progressDocumentCount = env->GetFieldID(cls_C4ReplStatus,
                                                               "progressDocumentCount", "J");
        if (!f_C4ReplStatus_progressUnitsTotal)
            return false;

        f_C4ReplStatus_errorDomain = env->GetFieldID(cls_C4ReplStatus, "errorDomain", "I");
        if (!f_C4ReplStatus_errorDomain)
            return false;

        f_C4ReplStatus_errorCode = env->GetFieldID(cls_C4ReplStatus, "errorCode", "I");
        if (!f_C4ReplStatus_errorCode)
            return false;

        f_C4ReplStatus_errorInternalInfo = env->GetFieldID(cls_C4ReplStatus, "errorInternalInfo",
                                                           "I");
        if (!f_C4ReplStatus_errorInternalInfo)
            return false;
    }

    // C4DocumentEnded, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentEnded");
        if (!localClass)
            return false;

        cls_C4DocEnded = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocEnded)
            return false;

        m_C4DocEnded_init = env->GetMethodID(cls_C4DocEnded, "<init>", "()V");
        if (!m_C4DocEnded_init)
            return false;

        f_C4DocEnded_docID = env->GetFieldID(cls_C4DocEnded, "docID", "Ljava/lang/String;");
        if (!f_C4DocEnded_docID)
            return false;

        f_C4DocEnded_revID = env->GetFieldID(cls_C4DocEnded, "revID", "Ljava/lang/String;");
        if (!f_C4DocEnded_revID)
            return false;

        f_C4DocEnded_flags = env->GetFieldID(cls_C4DocEnded, "flags", "I");
        if (!f_C4DocEnded_flags)
            return false;

        f_C4DocEnded_sequence = env->GetFieldID(cls_C4DocEnded, "sequence", "J");
        if (!f_C4DocEnded_sequence)
            return false;

        f_C4DocEnded_errorIsTransient = env->GetFieldID(cls_C4DocEnded, "errorIsTransient", "Z");
        if (!f_C4DocEnded_errorIsTransient)
            return false;

        f_C4DocEnded_errorDomain = env->GetFieldID(cls_C4DocEnded, "errorDomain", "I");
        if (!f_C4DocEnded_errorDomain)
            return false;

        f_C4DocEnded_errorCode = env->GetFieldID(cls_C4DocEnded, "errorCode", "I");
        if (!f_C4DocEnded_errorCode)
            return false;

        f_C4DocEnded_errorInternalInfo = env->GetFieldID(cls_C4DocEnded, "errorInternalInfo", "I");
        if (!f_C4DocEnded_errorInternalInfo)
            return false;
    }
    return true;
}

static jobject toJavaObject(JNIEnv *env, C4ReplicatorStatus status) {
    jobject obj = env->NewObject(cls_C4ReplStatus, m_C4ReplStatus_init);
    env->SetIntField(obj, f_C4ReplStatus_activityLevel, (int) status.level);
    env->SetLongField(obj, f_C4ReplStatus_progressUnitsCompleted,
                      (long) status.progress.unitsCompleted);
    env->SetLongField(obj, f_C4ReplStatus_progressUnitsTotal, (long) status.progress.unitsTotal);
    env->SetLongField(obj, f_C4ReplStatus_progressDocumentCount,
                      (long) status.progress.documentCount);
    env->SetIntField(obj, f_C4ReplStatus_errorDomain, (int) status.error.domain);
    env->SetIntField(obj, f_C4ReplStatus_errorCode, (int) status.error.code);
    env->SetIntField(obj, f_C4ReplStatus_errorInternalInfo, (int) status.error.internal_info);
    return obj;
}

static jobject toJavaDocumentEnded(JNIEnv *env, const C4DocumentEnded *document) {
    jobject obj = env->NewObject(cls_C4DocEnded, m_C4DocEnded_init);
    env->SetObjectField(obj, f_C4DocEnded_docID, toJString(env, document->docID));
    env->SetObjectField(obj, f_C4DocEnded_revID, toJString(env,document->revID));
    env->SetIntField(obj, f_C4DocEnded_flags, (int) document->flags);
    env->SetLongField(obj, f_C4DocEnded_sequence, (long) document->sequence);
    env->SetBooleanField(obj, f_C4DocEnded_errorIsTransient, (bool)document->errorIsTransient);
    env->SetIntField(obj, f_C4DocEnded_errorDomain, (int) document->error.domain);
    env->SetIntField(obj, f_C4DocEnded_errorCode, (int) document->error.code);
    env->SetIntField(obj, f_C4DocEnded_errorInternalInfo, (int) document->error.internal_info);
    return obj;
}

static jobjectArray toJavaDocumentEndedArray(JNIEnv *env, int arraySize, const C4DocumentEnded* array[]) {
    jobjectArray ds = env->NewObjectArray(arraySize, cls_C4DocEnded, NULL);
    for (int i = 0; i < arraySize; i++) {
        jobject d = toJavaDocumentEnded(env, array[i]);
        env->SetObjectArrayElement(ds, i, d);
    }
    return ds;
}

static std::vector<jobject>	contexts;

static jobject storeContext(JNIEnv* env, jobject jcontext) {
    if (jcontext == NULL)
        return NULL;

    jobject gContext = env->NewGlobalRef(jcontext);
    contexts.push_back(gContext);
    return gContext;
}

static void releaseContext(JNIEnv* env, jobject jcontext) {
    if (jcontext == NULL)
        return;

    jobject storedContext = NULL;
    jobject gContext = NULL;
    if (jcontext != NULL)
        gContext = env->NewGlobalRef(jcontext);

    if (gContext != NULL) {
        int i = 0;
        for (; i < contexts.size(); i++) {
            jobject c = contexts[i];
            if (env->IsSameObject(c, gContext)) {
                storedContext = c;
                break;
            }
        }
        env->DeleteGlobalRef(gContext);

        if (storedContext != NULL) {
            env->DeleteGlobalRef(storedContext);
            contexts.erase(contexts.begin() + i);
        }
    }
}

/**
 * Callback a client can register, to get progress information.
 * This will be called on arbitrary background threads, and should not block.
 *
 * @param repl
 * @param status
 * @param ctx
 */
static void statusChangedCallback(C4Replicator *repl, C4ReplicatorStatus status, void *ctx) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Replicator, m_C4Replicator_statusChangedCallback,
                                  (jlong) repl,
                                  toJavaObject(env, status));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Replicator, m_C4Replicator_statusChangedCallback,
                                      (jlong) repl,
                                      toJavaObject(env, status));
            if (gJVM->DetachCurrentThread() != 0)
                LOGE("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            LOGE("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

/**
 * Callback a client can register, to hear about errors replicating individual documents.
 *
 * @param repl
 * @param pushing
 * @param numDocs
 * @param documentEnded
 * @param ctx
 */
static void documentEndedCallback(C4Replicator *repl, bool pushing, size_t numDocs,
                                  const C4DocumentEnded* documentEnded[], void *ctx) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Replicator,
                                  m_C4Replicator_documentEndedCallback,
                                  (jlong) repl,
                                  pushing,
                                  toJavaDocumentEndedArray(env, numDocs, documentEnded));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Replicator,
                                      m_C4Replicator_documentEndedCallback,
                                      (jlong) repl,
                                      pushing,
                                      toJavaDocumentEndedArray(env, numDocs, documentEnded));
            if (gJVM->DetachCurrentThread() != 0)
                LOGE("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            LOGE("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

static jboolean replicationFilter(C4String docID, C4RevisionFlags flags, FLDict dict, bool isPush, void *ctx) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Replicator,
                                           m_C4Replicator_validationFunction,
                                           toJString(env,docID),
                                           flags,
                                           (jlong)dict,
                                           isPush,
                                           (jobject)ctx);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Replicator,
                                               m_C4Replicator_validationFunction,
                                               toJString(env,docID),
                                               flags,
                                               (jlong)dict,
                                               isPush,
                                               (jobject)ctx);
            if (gJVM->DetachCurrentThread() != 0)
                LOGE("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            LOGE("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
    return (jboolean)res;
}

/*
 Callback that can choose to reject an incoming pulled revision by returning false.
        (Note: In the case of an incoming revision, no flags other than 'deletion' and
        'hasAttachments' will be set.)
 *
 * @param docID
 * @param flags
 * @param dict
 * @param ctx
 */
static bool validationFunction(C4String docID, C4RevisionFlags flags, FLDict dict, void *ctx) {
    return (bool)replicationFilter(docID, flags, dict, false, ctx);
}

/*
 Callback that can stop a local revision from being pushed by returning false.
        (Note: In the case of an incoming revision, no flags other than 'deletion' and
        'hasAttachments' will be set.)
 *
 * @param docID
 * @param flags
 * @param dict
 * @param ctx
 */
static bool pushFilterFunction(C4String docID, C4RevisionFlags flags, FLDict dict, void *ctx) {
     return (bool)replicationFilter(docID, flags, dict, true, ctx);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    create
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;JIILjava/lang/Object;ILjava/lang/Object;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;[B)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4Replicator_create(
        JNIEnv *env,
        jclass clazz,
        jlong jdb,
        jstring jscheme,
        jstring jhost,
        jint jport,
        jstring jpath,
        jstring jremoteDBName,
        jlong jotherLocalDB,
        jint jpush,
        jint jpull,
        jobject jSocketFactoryContext,
        jint jframing,
        jobject jReplicatorContext,
        jobject pushFilter,
        jobject pullFilter,
        jbyteArray joptions) {
    jstringSlice scheme(env, jscheme);
    jstringSlice host(env, jhost);
    jstringSlice path(env, jpath);
    jstringSlice remoteDBName(env, jremoteDBName);
    jbyteArraySlice options(env, joptions, false);

    C4Address c4Address = {};
    c4Address.scheme = scheme;
    c4Address.hostname = host;
    c4Address.port = jport;
    c4Address.path = path;

    C4SocketFactory socketFactory = {};
    socketFactory = socket_factory();
    socketFactory.context = storeContext(env, jSocketFactoryContext);
    socketFactory.framing = (C4SocketFraming)jframing;

    C4ReplicatorParameters params = {};
    params.push = (C4ReplicatorMode) jpush;
    params.pull = (C4ReplicatorMode) jpull;
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.onDocumentsEnded = &documentEndedCallback;
    if(pushFilter != NULL) params.pushFilter = &pushFilterFunction;
    if(pullFilter != NULL) params.validationFunc = &validationFunction;
    params.callbackContext = storeContext(env, jReplicatorContext);
    params.socketFactory = &socketFactory;

    C4Error error;
    C4Replicator *repl = c4repl_new((C4Database *) jdb,
                                    c4Address,
                                    remoteDBName,
                                    (C4Database *) jotherLocalDB,
                                    params,
                                    &error);
    if (!repl) {
        throwError(env, error);
        return 0;
    }
    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    createWithSocket
 * Signature: (JJIILjava/lang/Object;[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_createWithSocket(JNIEnv *env,
                                                          jclass clazz,
                                                          jlong jdb,
                                                          jlong jopenSocket,
                                                          jint jpush,
                                                          jint jpull,
                                                          jobject jReplicatorContext,
                                                          jbyteArray joptions) {
    C4Database *db = (C4Database *) jdb;
    C4Socket *openSocket = (C4Socket *) jopenSocket;
    jbyteArraySlice options(env, joptions, false);

    C4ReplicatorParameters params = {};
    params.push = (C4ReplicatorMode) jpush;
    params.pull = (C4ReplicatorMode) jpull;
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.callbackContext = storeContext(env, jReplicatorContext);

    C4Error error;
    C4Replicator *repl = c4repl_newWithSocket(db, openSocket, params, &error);
    if (!repl) {
        throwError(env, error);
        return 0;
    }
    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    free
 * Signature: (JLjava/lang/Object;Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_free(JNIEnv *env,
                                              jclass clazz,
                                              jlong repl,
                                              jobject replicatorContext,
                                              jobject socketFactoryContext) {
    releaseContext(env, replicatorContext);
    releaseContext(env, socketFactoryContext);
    c4repl_free((C4Replicator *) repl);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_stop(JNIEnv *env, jclass clazz, jlong repl) {
    c4repl_stop((C4Replicator *) repl);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    getStatus
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4ReplicatorStatus;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_getStatus(JNIEnv *env, jclass clazz, jlong repl) {
    C4ReplicatorStatus status = c4repl_getStatus((C4Replicator *) repl);
    return toJavaObject(env, status);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    getResponseHeaders
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_getResponseHeaders(JNIEnv *env, jclass clazz, jlong repl) {
    C4Slice s = c4repl_getResponseHeaders((C4Replicator *) repl);
    jbyteArray res = toJByteArray(env, s);
    return res;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    mayBeTransient
 * Signature: (III)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_mayBeTransient(JNIEnv *env, jclass clazz,
                                                        jint domain, jint code, jint ii) {
    C4Error c4Error = {.domain = (C4ErrorDomain) domain, .code= code, .internal_info = ii};
    return c4error_mayBeTransient(c4Error);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    mayBeNetworkDependent
 * Signature: (III)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_mayBeNetworkDependent(JNIEnv *env, jclass clazz,
                                                               jint domain, jint code, jint ii) {
    C4Error c4Error = {.domain = (C4ErrorDomain) domain, .code= code, .internal_info = ii};
    return c4error_mayBeNetworkDependent(c4Error);
}

