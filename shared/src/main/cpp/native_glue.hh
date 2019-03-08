//
// native_glue.hh
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

#ifndef native_glue_hpp
#define native_glue_hpp

#include <jni.h>
#include <vector>
#include <c4.h>
#include "RefCounted.hh"
#include "fleece/Fleece.h"
#include "fleece/slice.hh"

#include "logging.h"

namespace litecore {
    namespace jni {

        using namespace fleece;

        // Soft limit of number of local JNI refs to use. Even using PushLocalFrame(), you may not get as
        // many refs as you asked for. At least, that's what happens on Android: the new frame won't have
        // more than 512 refs available. So 200 is being conservative.
        static const jsize MaxLocalRefsToUse = 200;

        extern JavaVM *gJVM;

        void deleteGlobalRef(jobject gRef);

        bool initC4Observer(JNIEnv *);   // Implemented in native_c4observer.cc
        bool initC4Replicator(JNIEnv *); // Implemented in native_c4replicator.cc
        bool initC4Socket(JNIEnv *);     // Implemented in native_c4socket.cc

        ssize_t UTF8ToModifiedUTF8(const char* input, const char** output, size_t len);

        // Creates a temporary slice value from a Java String object
        class jstringSlice {
        public:
            jstringSlice(JNIEnv *env, jstring js);

            ~jstringSlice();

            jstringSlice(jstringSlice &&s) // move constructor
                    : _slice(s._slice), _env(s._env), _jstr(s._jstr) {
                s._env = nullptr;
                s._slice = nullslice;
            }

            operator slice() { return _slice; }

            operator C4Slice() { return {_slice.buf, _slice.size}; }

            // Copies the string data and releases the JNI local ref.
            void copyAndReleaseRef();

            const char* cStr();

        private:
            static void UTF8ToModifiedUTF8(const char* input, char* output);

            slice _slice;
            JNIEnv *_env;
            jstring _jstr;
        };


        // Creates a temporary slice value from a Java byte[], attempting to avoid copying
        class jbyteArraySlice {
        public:
            // Warning: If `critical` is true, you cannot make any further JNI calls (except other
            // critical accesses) until this object goes out of scope or is deleted.
            jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical = false);

            ~jbyteArraySlice();

            jbyteArraySlice(jbyteArraySlice &&s) // move constructor
                    : _slice(s._slice), _env(s._env), _jbytes(s._jbytes),
                      _critical(s._critical) { s._slice = nullslice; }

            operator slice() { return _slice; }

            operator C4Slice() { return {_slice.buf, _slice.size}; }

            // Copies a Java byte[] to an alloc_slice
            static alloc_slice copy(JNIEnv *env, jbyteArray jbytes);

        private:
            slice _slice;
            JNIEnv *_env;
            jbyteArray _jbytes;
            bool _critical;
        };

        class JNIRef : public RefCounted {
        public:
            JNIRef(JNIEnv *env, jobject native) {
                if (env != NULL && native != NULL) {
                    _native = env->NewGlobalRef(native);
                } else {
                    _native = NULL;
                }
            }

            ~JNIRef() {
                if (_native != NULL) {
                    deleteGlobalRef(_native);
                    _native = NULL;
                }
            }

            jobject native() {
                return _native;
            }

        private:
            jobject _native;
        };

        typedef Retained<JNIRef> JNative;

        // Creates a Java String from the contents of a C4Slice.

        jstring toJString(JNIEnv *, C4Slice);

        jstring toJString(JNIEnv *, C4SliceResult);

        // Creates a Java byte[] from the contents of a C4Slice.

        jbyteArray toJByteArray(JNIEnv *, C4Slice);

        jbyteArray toJByteArray(JNIEnv *, C4SliceResult);

        // Sets a Java exception based on the LiteCore error.
        void throwError(JNIEnv *, C4Error);

        // Copies an encryption key to a C4EncryptionKey. Returns false on exception.
        bool getEncryptionKey(JNIEnv *env,
                              jint keyAlg,
                              jbyteArray jKeyBytes,
                              C4EncryptionKey *outKey);

    }
}

#endif /* native_glue_hpp */
