//
// native_c4base.cc
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

#include "com_couchbase_lite_internal_core_C4Base.h"

#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// Java_com_couchbase_lite_internal_core_C4Base
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4Base
 * Method:    getMessage
 * Signature: (III)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4Base_getMessage(JNIEnv *env, jclass clazz, jint jdomain, jint jcode,
                                              jint jinfo) {
    C4Error c4err = {(C4ErrorDomain) jdomain, (int32_t) jcode, (int32_t) jinfo};
    C4StringResult msg = c4error_getMessage(c4err);
    jstring result = toJString(env, msg);
    c4slice_free(msg);
    return result;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Base
 * Method:    setTempDir
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Base_setTempDir
        (JNIEnv *env, jclass clazz, jstring jtempDir) {
    jstringSlice tempDir(env, jtempDir);
    c4_setTempDir(tempDir);
}
