//
// native_c4socket.cc
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
#include <c4Socket.h>
#include "com_couchbase_lite_internal_core_C4Socket.h"
#include "socket_factory.h"
#include "native_glue.hh"
#include "logging.h"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// Callback method IDs to C4Socket
// ----------------------------------------------------------------------------
// C4Socket
static jclass cls_C4Socket;                   // global reference to C4Socket
static jmethodID m_C4Socket_open;             // callback method for C4Socket.open(...)
static jmethodID m_C4Socket_write;            // callback method for C4Socket.write(...)
static jmethodID m_C4Socket_completedReceive; // callback method for C4Socket.completedReceive(...)
static jmethodID m_C4Socket_requestClose;     // callback method for C4Socket.requestClose(...)
static jmethodID m_C4Socket_close;            // callback method for C4Socket.close(...)
static jmethodID m_C4Socket_dispose;          // callback method for C4Socket.dispose(...)

bool litecore::jni::initC4Socket(JNIEnv *env) {
    // Find C4Socket class and static methods for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Socket");
        if (!localClass)
            return false;

        cls_C4Socket = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4Socket)
            return false;

        m_C4Socket_open = env->GetStaticMethodID(
                cls_C4Socket,
                "open",
                "(JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;[B)V");
        if (!m_C4Socket_open)
            return false;

        m_C4Socket_write = env->GetStaticMethodID(cls_C4Socket,
                                                  "write",
                                                  "(J[B)V");
        if (!m_C4Socket_write)
            return false;

        m_C4Socket_completedReceive = env->GetStaticMethodID(cls_C4Socket,
                                                             "completedReceive",
                                                             "(JJ)V");
        if (!m_C4Socket_completedReceive)
            return false;

        m_C4Socket_close = env->GetStaticMethodID(cls_C4Socket,
                                                  "close",
                                                  "(J)V");
        if (!m_C4Socket_close)
            return false;

        m_C4Socket_requestClose = env->GetStaticMethodID(cls_C4Socket,
                                                         "requestClose",
                                                         "(JILjava/lang/String;)V");
        if (!m_C4Socket_requestClose)
            return false;

        m_C4Socket_dispose = env->GetStaticMethodID(cls_C4Socket,
                                                         "dispose",
                                                         "(J)V");
        if (!m_C4Socket_dispose)
            return false;
    }

    return true;
}

// ----------------------------------------------------------------------------
// C4SocketFactory implementation
// ----------------------------------------------------------------------------
static void
socket_open(C4Socket *socket, const C4Address *addr, C4Slice options, void *socketFactoryContext) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket,
                                  m_C4Socket_open,
                                  (jlong) socket,
                                  (jobject)socketFactoryContext,
                                  toJString(env, addr->scheme),
                                  toJString(env, addr->hostname),
                                  addr->port,
                                  toJString(env, addr->path),
                                  toJByteArray(env, options));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket,
                                      m_C4Socket_open,
                                      (jlong) socket,
                                      (jobject)socketFactoryContext,
                                      toJString(env, addr->scheme),
                                      toJString(env, addr->hostname),
                                      addr->port,
                                      toJString(env, addr->path),
                                      toJByteArray(env, options));
            if (gJVM->DetachCurrentThread() != 0) {
                LOGE("socket_open(): Failed to detach the current thread from a Java VM");
            }
        } else {
            LOGE("socket_open(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_open(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

static void socket_write(C4Socket *socket, C4SliceResult allocatedData) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket,
                                  m_C4Socket_write,
                                  (jlong) socket,
                                  toJByteArray(env, allocatedData));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket,
                                      m_C4Socket_write,
                                      (jlong) socket,
                                      toJByteArray(env, allocatedData));
            if (gJVM->DetachCurrentThread() != 0) {
                LOGE("socket_write(): Failed to detach the current thread from a Java VM");
            }
        } else {
            LOGE("socket_write(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_write(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
    c4slice_free(allocatedData);
}

static void socket_completedReceive(C4Socket *socket, size_t byteCount) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket,
                                  m_C4Socket_completedReceive,
                                  (jlong) socket,
                                  (jlong) byteCount);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket,
                                      m_C4Socket_completedReceive,
                                      (jlong) socket,
                                      (jlong) byteCount);
            if (gJVM->DetachCurrentThread() != 0) {
                LOGE("socket_completedReceive(): Failed to detach the current thread from a Java VM");
            }
        } else {
            LOGE("socket_completedReceive(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_completedReceive(): Failed to get the environment: getEnvStat -> %d",
             getEnvStat);
    }
}

static void socket_requestClose(C4Socket *socket, int status, C4String messageSlice) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket,
                                  m_C4Socket_requestClose,
                                  (jlong) socket,
                                  (jint) status,
                                  toJString(env, messageSlice));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket,
                                      m_C4Socket_requestClose,
                                      (jlong) socket,
                                      (jint) status,
                                      toJString(env, messageSlice));
            if (gJVM->DetachCurrentThread() != 0) {
                LOGE("socket_requestClose(): Failed to detach the current thread from a Java VM");
            }
        } else {
            LOGE("socket_requestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_requestClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

static void socket_close(C4Socket *socket) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_close, (jlong) socket);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_close, (jlong) socket);
            if (gJVM->DetachCurrentThread() != 0) {
                LOGE("socket_close(): Failed to detach the current thread from a Java VM");
            }
        } else {
            LOGE("socket_close(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_close(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

static std::vector<jobject> nativeHandles;

static void socket_dispose(C4Socket *socket) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_dispose, (jlong) socket);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
            env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_dispose, (jlong) socket);
        } else {
            LOGE("socket_dispose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        LOGE("socket_dispose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }

    if (socket->nativeHandle != NULL) {
        jobject handle = NULL;
        int i = 0;
        for (; i < nativeHandles.size(); i++) {
            jobject h = nativeHandles[i];
            if (h == socket->nativeHandle) {
                handle = h;
                break;
            }
        }
        if (handle != NULL) {
            env->DeleteGlobalRef(handle);
            nativeHandles.erase(nativeHandles.begin() + i);
        }
    }

    if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->DetachCurrentThread() != 0) {
            LOGE("socket_dispose(): Failed to detach the current thread from a Java VM");
        }
    }
}

static const C4SocketFactory kSocketFactory{
        //.framing            = kC4WebSocketClientFraming, //kC4NoFraming
        .framing            = kC4NoFraming, //kC4NoFraming
        .open               = &socket_open,
        .write              = &socket_write,
        .completedReceive   = &socket_completedReceive,
        .requestClose       = &socket_requestClose,
        .close              = &socket_close,
        .dispose            = &socket_dispose,
};

const C4SocketFactory socket_factory() {
    return kSocketFactory;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Socket
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    gotHTTPResponse
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_gotHTTPResponse(JNIEnv *env, jclass clazz, jlong socket,
                                                     jint httpStatus,
                                                     jbyteArray jresponseHeadersFleece) {
    jbyteArraySlice responseHeadersFleece(env, jresponseHeadersFleece, false);
    c4socket_gotHTTPResponse((C4Socket *) socket, httpStatus, responseHeadersFleece);
}
/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    opened
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_opened(JNIEnv *env, jclass clazz, jlong jsocket) {
    C4Socket *socket = (C4Socket *) jsocket;
    c4socket_opened(socket);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closed
 * Signature: (JIILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_closed(JNIEnv *env, jclass clazz,
                                            jlong jSocket,
                                            jint domain,
                                            jint code,
                                            jstring message) {
    C4Socket *socket = (C4Socket *) jSocket;
    jstringSlice sliceMessage(env, message);
    C4Error error = c4error_make((C4ErrorDomain) domain, code, sliceMessage);
    c4socket_closed((C4Socket *) socket, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closeRequested
 * Signature: (JILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_closeRequested(JNIEnv *env, jclass clazz,
                                                    jlong jSocket,
                                                    jint status,
                                                    jstring jmessage) {
    C4Socket *socket = (C4Socket *) jSocket;
    jstringSlice message(env, jmessage);
    c4socket_closeRequested((C4Socket *) socket, (int) status, message);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    completedWrite
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_completedWrite(JNIEnv *env, jclass clazz,
                                                    jlong jSocket,
                                                    jlong jByteCount) {
    C4Socket *socket = (C4Socket *) jSocket;
    size_t byteCount = (size_t) jByteCount;
    c4socket_completedWrite(socket, byteCount);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    received
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_received(JNIEnv *env, jclass clazz,
                                              jlong jSocket,
                                              jbyteArray jdata) {
    C4Socket *socket = (C4Socket *) jSocket;
    jbyteArraySlice data(env, jdata, false);
    c4socket_received((C4Socket *) socket, data);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    fromNative
 * Signature: (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;I)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Socket_fromNative(JNIEnv *env,
                                                jclass clazz,
                                                jobject jnativeHandle,
                                                jstring jscheme,
                                                jstring jhost,
                                                jint jport,
                                                jstring jpath,
                                                jint jframing) {
    jstringSlice scheme(env, jscheme);
    jstringSlice host(env, jhost);
    jstringSlice path(env, jpath);

    C4Address c4Address = {};
    c4Address.scheme = scheme;
    c4Address.hostname = host;
    c4Address.port = jport;
    c4Address.path = path;

    jobject gNativeHandle = env->NewGlobalRef(jnativeHandle);
    nativeHandles.push_back(gNativeHandle);

    C4SocketFactory socketFactory = socket_factory();
    socketFactory.framing = (C4SocketFraming)jframing;
    socketFactory.context = gNativeHandle;
    C4Socket *c4socket = c4socket_fromNative(socketFactory, gNativeHandle, &c4Address);
    return (jlong) c4socket;
}
