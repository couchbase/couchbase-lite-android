//
// native_encoder.cc
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
#include "fleece/Fleece.hh"
#include "com_couchbase_litecore_fleece_Encoder.h"

using namespace fleece;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// Encoder
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_litecore_fleece_Encoder_init(JNIEnv *env, jclass clazz) {
    return (jlong) new Encoder();
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    initWithFLEncoder
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_fleece_Encoder_initWithFLEncoder(JNIEnv *env, jclass clazz,
                                                             jlong jflenc) {
    return (jlong) new Encoder((FLEncoder) jflenc);
}


/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_fleece_Encoder_free(JNIEnv *env, jclass clazz, jlong jenc) {
    delete (Encoder *) jenc;
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    release
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_fleece_Encoder_release(JNIEnv *env, jclass clazz, jlong jenc) {
    Encoder *enc = (Encoder *) jenc;
    enc->detach();
    delete enc;
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    getFLEncoder
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_fleece_Encoder_getFLEncoder(JNIEnv *env, jclass clazz, jlong jenc) {
    Encoder *enc = (Encoder *) jenc;
    FLEncoder flenc = (FLEncoder) *enc;
    return (jlong) flenc;
}
/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeNull
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeNull(JNIEnv *env, jclass clazz, jlong jenc) {
    return ((Encoder *) jenc)->writeNull();
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeBool
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeBool(JNIEnv *env, jclass clazz, jlong jenc,
                                                     jboolean jvalue) {
    return ((Encoder *) jenc)->writeBool((bool) jvalue);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeInt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeInt(JNIEnv *env, jclass clazz, jlong jenc,
                                                    jlong jvalue) {
    return ((Encoder *) jenc)->writeInt((int64_t) jvalue);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeFloat
 * Signature: (JF)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeFloat(JNIEnv *env, jclass clazz, jlong jenc,
                                                      jfloat jvalue) {
    return ((Encoder *) jenc)->writeFloat((float) jvalue);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeDouble
 * Signature: (JD)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeDouble(JNIEnv *env, jclass clazz, jlong jenc,
                                                       jdouble jvalue) {
    return ((Encoder *) jenc)->writeDouble((double) jvalue);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeString
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeString(JNIEnv *env, jclass clazz, jlong jenc,
                                                       jstring jvalue) {
    jstringSlice value(env, jvalue);
    slice s = value;
    return ((Encoder *) jenc)->writeString(s);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeData
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeData(JNIEnv *env, jclass clazz, jlong jenc,
                                                     jbyteArray jvalue) {
    jbyteArraySlice value(env, jvalue, true);
    slice s = value;
    return ((Encoder *) jenc)->writeData(s);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeValue
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeValue(JNIEnv *env, jclass clazz, jlong jenc,
                                                      jlong jvalue) {
    return ((Encoder *) jenc)->writeValue((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    beginArray
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_beginArray(JNIEnv *env, jclass clazz, jlong jenc,
                                                      jlong jreserve) {
    return ((Encoder *) jenc)->beginArray((size_t) jreserve);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    endArray
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_endArray(JNIEnv *env, jclass clazz, jlong jenc) {
    return ((Encoder *) jenc)->endArray();
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    beginDict
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_beginDict(JNIEnv *env, jclass clazz, jlong jenc,
                                                     jlong jreserve) {
    return ((Encoder *) jenc)->beginDict((size_t) jreserve);
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    writeKey
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_writeKey(JNIEnv *env, jclass clazz, jlong jenc,
                                                    jstring jkey) {
    if (jkey == NULL) return false;
    jstringSlice key(env, jkey);
    slice s = key;
    return ((Encoder *) jenc)->writeKey({s.buf, s.size});
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    endDict
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_fleece_Encoder_endDict(JNIEnv *env, jclass clazz, jlong jenc) {
    return ((Encoder *) jenc)->endDict();
}

/*
 * Class:     com_couchbase_litecore_fleece_Encoder
 * Method:    finish
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_fleece_Encoder_finish(JNIEnv *env, jclass clazz, jlong jenc) {
    FLError error = kFLNoError;
    alloc_slice result = ((Encoder *) jenc)->finish(&error);
    if (error != kFLNoError)
        throwError(env, {FleeceDomain, error});
    return (jlong) new alloc_slice(result);
}
