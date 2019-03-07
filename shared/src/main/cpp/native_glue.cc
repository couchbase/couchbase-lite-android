//
// native_glue.cc
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
#include <queue>
#include <new>

using namespace litecore;
using namespace litecore::jni;
using namespace std;

namespace litecore { namespace jni {
        void UTF8CharToModifiedUTF8(const char *input, char *output);
        void ModifiedUTF8ToUTF8(char* input);
        void ModifiedUTF8CharToUTF8(char* input);
    }
}

void litecore::jni::UTF8CharToModifiedUTF8(const char *input, char *output) {
    char c = input[0];
    char c1 = input[1] & 0x3F;
    char c2 = input[2] & 0x3F;
    char c3 = input[3] & 0x3F;

    int unicodePoint = ((c & 0x07) << 18) | (c1 << 12) | (c2 << 6) | c3;
    unicodePoint -= 0x10000;

    int surrogates[2] { 0xD800 | (unicodePoint >> 10), 0xDC00 | (unicodePoint & 0x3FF) };

    output[0] = (surrogates[0]>>12 & 0x0F) | 0xE0;
    output[1] = (surrogates[0]>>6  & 0x3F) | 0x80;
    output[2] = (surrogates[0]     & 0x3F) | 0x80;
    output[3] = (surrogates[1]>>12 & 0x0F) | 0xE0;
    output[4] = (surrogates[1]>>6  & 0x3F) | 0x80;
    output[5] = (surrogates[1]     & 0x3F) | 0x80;
}

ssize_t litecore::jni::UTF8ToModifiedUTF8(const char* input, const char** output, size_t len){
    // https://github.com/android-ndk/ndk/issues/283
    size_t extraBytes = 0;
    const auto unsignedInput = (const uint8_t *)input;

    // Need to figure out the actual length since each
    // 4-bytes of real UTF-8 is 6 bytes of modified UTF-8
    // but convert the bytes while we are at it
    for(size_t i = 0; i < len; i++) {
        if(unsignedInput[i] >= 0xF0) {
            // 0xF0 and above marks 4-byte sequences
            extraBytes += 2;

            // 3 + 1 from next loop iteration = 4 bytes advance
            i += 3;
        }
    }

    if(extraBytes == 0) {
        // No modifications necessary
        *output = nullptr;
        return len;
    }

    size_t newStrLen = len + extraBytes;
    char* newBytes = (char *)malloc(newStrLen);
    if(newBytes == nullptr) {
        *output = nullptr;
        return -1;
    }

    int offset = 0;
    for(size_t i = 0; i < len; i++) {
        if(unsignedInput[i] >= 0xF0) {
            UTF8CharToModifiedUTF8(input + i, newBytes + i + offset);
            i += 3;
            offset += 2;
        } else {
            newBytes[i+offset] = unsignedInput[i];
        }
    }

    *output = newBytes;
    return newStrLen;
}

void litecore::jni::ModifiedUTF8CharToUTF8(char *input) {
    char c = input[0];
    char c1 = input[1] & 0x3F;
    char c2 = input[2] & 0x3F;
    char d = input[3];
    char d1 = input[4] & 0x3F;
    char d2 = input[5] & 0x3F;

    int surrogate[2] = { ((c & 0x0F) << 12) | (c1 << 6) | c2, ((d & 0x0F) << 12) | (d1 << 6) | d2 };
    int codePoint = ((surrogate[0] - 0xD800) << 10) + (surrogate[1] - 0xDC00) + 0x10000;

    input[0] = 0xF0 | (codePoint  >> 18);
    input[1] = 0x80 | ((codePoint >> 12) & 0x3F);
    input[2] = 0x80 | ((codePoint >> 6) & 0x3F);
    input[3] = 0x80 | ((codePoint & 0x3F));
}

void litecore::jni::ModifiedUTF8ToUTF8(char *input) {
    size_t len = 0;
    size_t i = 0;
    bool needsModify = false;
    for(i = 0; input[i] != 0; i++) {
        if(input[i] == '\xed') {
            // According to https://docs.oracle.com/javase/1.5.0/docs/guide/jni/spec/types.html#wp16542
            // modified UTF-8 for codepoints > FFFF will always start with 0xED
            needsModify = true;
            ModifiedUTF8CharToUTF8(&input[i]);
            i += 5;
        }
    }

    if(!needsModify) {
        return;
    }

    len = i;
    size_t j = 0;
    for(i = 0; input[i] != 0; i++) {
        if((uint8_t)input[i] >= 0xF0) {
            i+= 3;
            size_t j;
            for(j = i + 1; j < len - 2; j++) {
                input[j] = input[j+2];
            }

            input[j] = 0;
        }
    }
}

/*
 * Will be called by JNI when the library is loaded
 *
 * NOTE:
 *  All resources allocated here are never released by application
 *  we rely on system to free all global refs when it goes away,
 *  the pairing function JNI_OnUnload() never get called at all.
 */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
    JNIEnv *env;
    if (jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK
        && initC4Observer(env)
        && initC4Replicator(env)
        && initC4Socket(env)) {
        assert(gJVM == nullptr);
        gJVM = jvm;
        return JNI_VERSION_1_6;
    } else {
        return JNI_ERR;
    }
}

namespace litecore {
    namespace jni {

        JavaVM *gJVM;

        void deleteGlobalRef(jobject gRef) {
            JNIEnv *env = NULL;
            jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (getEnvStat == JNI_OK) {
                env->DeleteGlobalRef(gRef);
            } else if (getEnvStat == JNI_EDETACHED) {
                if (gJVM->AttachCurrentThread(&env, NULL) == 0) {
                    env->DeleteGlobalRef(gRef);
                    if (gJVM->DetachCurrentThread() != 0) {
                    }
                }
            }
        }

        jstringSlice::jstringSlice(JNIEnv *env, jstring js)
                : _env(nullptr) {
            assert(env != nullptr);
            if (js != nullptr) {
                jboolean isCopy;
                _jstr = js;
                _env = env;

                char *cstr = (char *)env->GetStringUTFChars(js, &isCopy);
                assert(isCopy);
                ModifiedUTF8ToUTF8(cstr);
                if (!cstr)
                    return; // Would it be better to throw an exception?
                _slice = slice(cstr);
            }
        }

        jstringSlice::~jstringSlice() {
            if (_env)
                _env->ReleaseStringUTFChars(_jstr, (const char *) _slice.buf);
            else if (_slice.buf)
                free((void *) _slice.buf);        // detached
        }

        void jstringSlice::copyAndReleaseRef() {
            if (_env) {
                auto cstr = (const char *) _slice.buf;
                _slice = _slice.copy();
                _env->ReleaseStringUTFChars(_jstr, cstr);
                _env->DeleteLocalRef(_jstr);
                _env = nullptr;
            }
        }

        const char* jstringSlice::cStr() {
            return static_cast<const char *>(_slice.buf);
        };

        // ATTN: In critical, should not call any other JNI methods.
        // http://docs.oracle.com/javase/6/docs/technotes/guides/jni/spec/functions.html
        jbyteArraySlice::jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical)
                : _env(env),
                  _jbytes(jbytes),
                  _critical(critical) {
            if (jbytes == nullptr) {
                _slice = nullslice;
                return;
            }

            jboolean isCopy;
            if (critical)
                _slice.setBuf(env->GetPrimitiveArrayCritical(jbytes, &isCopy));
            else
                _slice.setBuf(env->GetByteArrayElements(jbytes, &isCopy));
            _slice.setSize(env->GetArrayLength(jbytes));
        }

        jbyteArraySlice::~jbyteArraySlice() {
            if (_slice.buf) {
                if (_critical)
                    _env->ReleasePrimitiveArrayCritical(_jbytes, (void *) _slice.buf, JNI_ABORT);
                else
                    _env->ReleaseByteArrayElements(_jbytes, (jbyte *) _slice.buf, JNI_ABORT);
            }
        }

        alloc_slice jbyteArraySlice::copy(JNIEnv *env, jbyteArray jbytes) {
            jsize size = env->GetArrayLength(jbytes);
            alloc_slice slice(size);
            env->GetByteArrayRegion(jbytes, 0, size, (jbyte *) slice.buf);
            return slice;
        }

        void throwError(JNIEnv *env, C4Error error) {
            if (env->ExceptionOccurred())
                return;
            jclass xclass = env->FindClass("com/couchbase/lite/LiteCoreException");
            assert(xclass); // if we can't even throw an exception, we're really fuxored
            jmethodID m = env->GetStaticMethodID(xclass, "throwException",
                                                 "(IILjava/lang/String;)V");
            assert(m);

            C4SliceResult msgSlice = c4error_getMessage(error);
            jstring msg = toJString(env, msgSlice);
            c4slice_free(msgSlice);

            env->CallStaticVoidMethod(xclass, m, (jint) error.domain, (jint) error.code, msg);
        }

        jstring toJString(JNIEnv *env, C4Slice s) {
            if (s.buf == nullptr)
                return nullptr;
            std::string utf8Buf((char *) s.buf, s.size);
            // NOTE: This return value will be taken care by JVM. So not necessary to free by our self
            return env->NewStringUTF(utf8Buf.c_str());
        }

        jstring toJString(JNIEnv *env, C4SliceResult s) {
            return toJString(env, (C4Slice) s);
        }

        jbyteArray toJByteArray(JNIEnv *env, C4Slice s) {
            if (s.buf == nullptr)
                return nullptr;
            // NOTE: Local reference is taken care by JVM.
            // http://docs.oracle.com/javase/6/docs/technotes/guides/jni/spec/functions.html#global_local
            jbyteArray array = env->NewByteArray((jsize) s.size);
            if (array)
                env->SetByteArrayRegion(array, 0, (jsize) s.size, (const jbyte *) s.buf);
            return array;
        }

        jbyteArray toJByteArray(JNIEnv *env, C4SliceResult s) {
            return toJByteArray(env, (C4Slice) s);
        }

        bool getEncryptionKey(JNIEnv *env, jint keyAlg, jbyteArray jKeyBytes,
                              C4EncryptionKey *outKey) {
            outKey->algorithm = (C4EncryptionAlgorithm) keyAlg;
            if (keyAlg != kC4EncryptionNone) {
                jbyteArraySlice keyBytes(env, jKeyBytes);
                fleece::slice keySlice = keyBytes;
                if (!keySlice.buf || keySlice.size > sizeof(outKey->bytes)) {
                    throwError(env, C4Error{LiteCoreDomain, kC4ErrorCrypto});
                    return false;
                }
                memset(outKey->bytes, 0, sizeof(outKey->bytes));
                memcpy(outKey->bytes, keySlice.buf, keySlice.size);
            }
            return true;
        }
    }
}
