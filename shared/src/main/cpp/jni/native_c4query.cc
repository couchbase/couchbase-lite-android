//
// native_c4query.cc
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
#include <c4Base.h>
#include "com_couchbase_litecore_C4Query.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_litecore_C4Query
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    init
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Query_init(JNIEnv *env, jclass clazz, jlong db, jstring jexpr) {
    jstringSlice expr(env, jexpr);
    C4Error error = {};
    C4Query *query = c4query_new((C4Database *) db, expr, &error);
    if (!query)
        throwError(env, error);
    return (jlong) query;
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Query_free(JNIEnv *env, jclass clazz, jlong jquery) {
    c4query_free((C4Query *) jquery);
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    explain
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_litecore_C4Query_explain(JNIEnv *env, jclass clazz, jlong jquery) {
    C4StringResult result = c4query_explain((C4Query *) jquery);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}


/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    columnCount
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_litecore_C4Query_columnCount(JNIEnv *env, jclass clazz, jlong jquery) {
    return c4query_columnCount((C4Query *) jquery);
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    run
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Query_run(JNIEnv *env, jclass clazz,
                                        jlong jquery,
                                        jboolean jrankFullText,
                                        jlong jparameters) {
    C4QueryOptions options = {
            (bool) jrankFullText
    };

    alloc_slice *params = (alloc_slice *) jparameters;
    C4Error error = {};
    C4QueryEnumerator *e = c4query_run((C4Query *) jquery, &options,
            (C4Slice){params->buf, params->size}, &error);
    if (!e)
        throwError(env, error);
    return (jlong) e;
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    getFullTextMatched
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_litecore_C4Query_getFullTextMatched
        (JNIEnv *env, jclass clazz, jlong jquery, jlong jterm) {
    C4Error error = {};
    const C4FullTextMatch *term = (const C4FullTextMatch *) jterm;
    C4SliceResult s = c4query_fullTextMatched((C4Query *) jquery, term, &error);
    jbyteArray res = toJByteArray(env, s);
    c4slice_free(s);
    return res;
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Z)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_litecore_C4Query_createIndex(JNIEnv *env, jclass clazz, jlong jdb, jstring jname,
                                                jstring jexpressionsJSON, jint indexType,
                                                jstring jlanguage, jboolean ignoreDiacritics) {
    jstringSlice name(env, jname);
    jstringSlice expressionsJSON(env, jexpressionsJSON);
    jstringSlice language(env, jlanguage);
    C4IndexOptions options = {};
    slice sLang = language;
    if(sLang.buf != NULL)
        options.language = (const char *)sLang.buf;
    options.ignoreDiacritics = (bool)ignoreDiacritics;
    C4Error error = {};
    bool res = c4db_createIndex((C4Database *) jdb, name, (C4Slice) expressionsJSON,
                                (C4IndexType) indexType, &options, &error);
    if (!res)
        throwError(env, error);
    return res;
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_litecore_C4Query_deleteIndex(JNIEnv *env, jclass clazz, jlong jdb,
                                                jstring jname) {
    jstringSlice name(env, jname);
    C4Error error = {};
    bool res = c4db_deleteIndex((C4Database *) jdb, name, &error);
    if (!res)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_litecore_C4Query
 * Method:    getIndexes
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_litecore_C4Query_getIndexes(JNIEnv *env, jclass clazz, jlong jdb) {
    C4SliceResult data = c4db_getIndexes((C4Database *) jdb, nullptr);
    return (jlong) FLValue_FromData({data.buf, data.size}, kFLTrusted);
}
