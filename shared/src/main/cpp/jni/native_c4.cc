//
// native_c4.cc
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
#include "com_couchbase_litecore_C4.h"
#include "com_couchbase_litecore_C4Log.h"
#include "com_couchbase_litecore_C4Key.h"
#include "mbedtls/pkcs5.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

static jclass cls_C4Log;
static jmethodID m_C4Log_logCallback;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_litecore_C4
 * Method:    setenv
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4_setenv(JNIEnv *env, jclass clazz, jstring jname, jstring jvalue,
                                      jint overwrite) {
    jstringSlice name(env, jname);
    jstringSlice value(env, jvalue);
    slice nameSlice = name;
    slice valueSlice = value;
    setenv(nameSlice.cString(), valueSlice.cString(), overwrite);
}

/*
 * Class:     com_couchbase_litecore_C4
 * Method:    getenv
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4_getenv(JNIEnv *env, jclass clazz, jstring jname) {
    jstringSlice name(env, jname);
    slice s = name;
    return env->NewStringUTF(getenv(s.cString()));
}

/*
 * Class:     com_couchbase_litecore_C4
 * Method:    getBuildInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_litecore_C4_getBuildInfo(JNIEnv *env, jclass clazz) {
    C4StringResult result = c4_getBuildInfo();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_litecore_C4
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_litecore_C4_getVersion(JNIEnv *env, jclass clazz) {
    C4StringResult result = c4_getVersion();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Log
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    setLevel
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Log_setLevel(JNIEnv *env, jclass clazz, jstring jdomain,
                                           jint jlevel) {
    jstringSlice domain(env, jdomain);
    slice s = domain;
    C4LogDomain logDomain = c4log_getDomain(s.cString(), false);
    if (logDomain)
        c4log_setLevel(logDomain, (C4LogLevel) jlevel);
}

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    log
 * Signature: (Ljava/lang/String;I;Ljava/lang/String)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Log_log(JNIEnv* env, jclass clazz, jstring jdomain, jint jlevel, jstring jmessage) {
    jstringSlice message(env, jmessage);

    jboolean isCopy;
    const char* domain = env->GetStringUTFChars(jdomain, &isCopy);
    C4LogDomain logDomain = c4log_getDomain(domain, false);
    c4slog(logDomain, (C4LogLevel)jlevel, message);

    if(isCopy) {
        free((void *)domain);
    }
}

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    getBinaryFileLevel
 * Signature: (V)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Log_getBinaryFileLevel(JNIEnv* env, jclass clazz) {
    return c4log_binaryFileLevel();
}

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    setBinaryFileLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Log_setBinaryFileLevel(JNIEnv* env, jclass clazz, jint level) {
    c4log_setBinaryFileLevel((C4LogLevel)level);
}

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    writeToBinaryFile
 * Signature: (Ljava/lang/String;IIJZLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Log_writeToBinaryFile(JNIEnv* env, jclass clazz, jstring jpath,
                                                    jint jlevel, jint jmaxrotatecount, jlong jmaxsize,
                                                    jboolean juseplaintext, jstring jheader) {
    jstringSlice path(env, jpath);
    jstringSlice header(env, jheader);
    C4LogFileOptions options {
        (C4LogLevel)jlevel,
        path,
        jmaxsize,
        jmaxrotatecount,
        (bool)juseplaintext,
        header
    };

    C4Error err;
    if(!c4log_writeToBinaryFile(options, &err)) {
        throwError(env, err);
    }
}

static void logCallback(C4LogDomain domain, C4LogLevel level, const char *fmt, va_list args) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if(getEnvStat == JNI_EDETACHED) {
        if (gJVM->AttachCurrentThread(&env, NULL) != 0) {
            LOGE("logCallback(): Failed to attach the current thread to a Java VM)");
            return;
        }
    } else if(getEnvStat != JNI_OK) {
        LOGE("logCallback(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
        return;
    }

    jstring message = env->NewStringUTF(fmt);
    const char* domainNameRaw = c4log_getDomainName(domain);
    jstring domainName = env->NewStringUTF(domainNameRaw);
    env->CallStaticVoidMethod(cls_C4Log, m_C4Log_logCallback, domainName, (jint)level, message);

    if(getEnvStat == JNI_EDETACHED) {
        if (gJVM->DetachCurrentThread() != 0) {
            LOGE("logCallback(): doRequestClose(): Failed to detach the current thread from a Java VM");
        }
    }
}

/*
 * Class:     com_couchbase_litecore_C4Log
 * Method:    setCallbackLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Log_setCallbackLevel(JNIEnv* env, jclass clazz, jint jlevel) {
    if(cls_C4Log == nullptr) {
        cls_C4Log = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
        if(!cls_C4Log) {
            C4Error err { .code = kC4ErrorUnexpectedError, .domain = LiteCoreDomain };
            throwError(env, err);
        }

        m_C4Log_logCallback = env->GetStaticMethodID(cls_C4Log,
                                                     "logCallback",
                                                     "(Ljava/lang/String;ILjava/lang/String;)V");

        if(!m_C4Log_logCallback) {
            C4Error err { .code = kC4ErrorUnexpectedError, .domain = LiteCoreDomain };
            throwError(env, err);
        }

        c4log_writeToCallback((C4LogLevel)jlevel, logCallback, true);
    }

    c4log_setCallbackLevel((C4LogLevel)jlevel);
}

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Key
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Key
 * Method:    pbkdf2
 * Signature: (Ljava/lang/String;[BII)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_couchbase_litecore_C4Key_pbkdf2
        (JNIEnv *env, jclass clazz, jstring jpassword, jbyteArray jsalt, jint jiteration,
         jint jkeyLen) {

    // PBKDF2 (Password-Based Key Derivation Function 2)
    // https://en.wikipedia.org/wiki/PBKDF2
    // https://www.ietf.org/rfc/rfc2898.txt
    //
    // algorithm: PBKDF2
    // hash: SHA1
    // iteration: ? (64000)
    // key length: ? (16)

    if (jpassword == NULL || jsalt == NULL)
        return NULL;

    // Password:
    const char *password = env->GetStringUTFChars(jpassword, NULL);
    int passwordSize = (int) env->GetStringLength(jpassword);

    // Salt:
    int saltSize = env->GetArrayLength(jsalt);
    unsigned char *salt = new unsigned char[saltSize];
    env->GetByteArrayRegion(jsalt, 0, saltSize, reinterpret_cast<jbyte *>(salt));

    // Rounds
    const int iteration = (const int) jiteration;

    // PKCS5 PBKDF2 HMAC SHA256
    const int keyLen = (const int) jkeyLen;
    unsigned char key[keyLen];

    mbedtls_md_context_t ctx;
    mbedtls_md_init(&ctx);

    const mbedtls_md_info_t *info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA1);
    if (info == NULL) {
        // error
        mbedtls_md_free(&ctx);
        env->ReleaseStringUTFChars(jpassword, password);
        delete[] salt;
        return NULL;
    }

    int status = 0;
    if ((status = mbedtls_md_setup(&ctx, info, 1)) == 0)
        status = mbedtls_pkcs5_pbkdf2_hmac(&ctx, (const unsigned char *) password, passwordSize,
                                           salt, saltSize, iteration, keyLen, key);

    mbedtls_md_free(&ctx);

    // Release memory:
    env->ReleaseStringUTFChars(jpassword, password);
    delete[] salt;

    // Return null if not success:
    if (status != 0)
        return NULL;

    // Return result:
    jbyteArray result = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(result, 0, keyLen, (jbyte *) key);
    return result;
}