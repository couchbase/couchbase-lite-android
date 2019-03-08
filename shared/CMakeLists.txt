#
# CMakeLists.txt
#
# Copyright (c) 2017 Couchbase, Inc All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.4.1)

project (LiteCoreJNI)

set(LITE_CORE_ROOT ${PROJECT_SOURCE_DIR}/../libs/couchbase-lite-core)

find_package(Java COMPONENTS Development)
include (UseJava)

# Android does not work with FindJNI
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    MESSAGE( STATUS "!!!!!!!CMAKE_SYSTEM_NAME: "         ${CMAKE_SYSTEM_NAME} )
    MESSAGE( STATUS "!!!!!!!ANDROID: "         ${ANDROID} )
    find_package(JNI)
endif()

MESSAGE( STATUS "Java_FOUND: "            ${Java_FOUND} )
MESSAGE( STATUS "Java_INCLUDE_DIRS: "     ${Java_INCLUDE_DIRS} )
MESSAGE( STATUS "JAVA_INCLUDE_PATH: "     ${JAVA_INCLUDE_PATH} )
MESSAGE( STATUS "JAVA_INCLUDE_PATH2: "    ${JAVA_INCLUDE_PATH2} )
MESSAGE( STATUS "Java_JAVAH_EXECUTABLE: " ${Java_JAVAH_EXECUTABLE} )

#
# Generate JNI header files
#
set(JNI_CLASSPATH  ${PROJECT_SOURCE_DIR}/src/main/java)
set(JNI_OUTPUT_DIR ${PROJECT_SOURCE_DIR}/src/main/cpp)
set(JNI_CLASSES
    com.couchbase.lite.internal.core.C4
    com.couchbase.lite.internal.core.C4Base
    com.couchbase.lite.internal.core.C4BlobKey
    com.couchbase.lite.internal.core.C4BlobStore
    com.couchbase.lite.internal.core.C4BlobReadStream
    com.couchbase.lite.internal.core.C4BlobWriteStream
    com.couchbase.lite.internal.core.C4Database
    com.couchbase.lite.internal.core.C4DatabaseObserver
    com.couchbase.lite.internal.core.C4DocEnumerator
    com.couchbase.lite.internal.core.C4Document
    com.couchbase.lite.internal.core.C4DocumentObserver
    com.couchbase.lite.internal.core.C4FullTextMatch
    com.couchbase.lite.internal.core.C4Key
    com.couchbase.lite.internal.core.C4Listener
    com.couchbase.lite.internal.core.C4Log
    com.couchbase.lite.internal.core.C4Prediction
    com.couchbase.lite.internal.core.C4Query
    com.couchbase.lite.internal.core.C4QueryEnumerator
    com.couchbase.lite.internal.core.C4RawDocument
    com.couchbase.lite.internal.core.C4Replicator
    com.couchbase.lite.internal.core.C4Socket
    com.couchbase.lite.internal.fleece.AllocSlice
    com.couchbase.lite.internal.fleece.Encoder
    com.couchbase.lite.internal.fleece.FLArray
    com.couchbase.lite.internal.fleece.FLArrayIterator
    com.couchbase.lite.internal.fleece.FLDict
    com.couchbase.lite.internal.fleece.FLDictIterator
    com.couchbase.lite.internal.fleece.FLEncoder
    com.couchbase.lite.internal.fleece.FLSliceResult
    com.couchbase.lite.internal.fleece.FLValue
    )

create_javah(TARGET JNIHeaders
    CLASSES ${JNI_CLASSES}
    CLASSPATH ${JNI_CLASSPATH}
    OUTPUT_DIR ${JNI_OUTPUT_DIR}
    DEPENDS ${JNI_CLASSPATH})

#
# JNI for litecore - shared library including litecore static library
#

# jni.h and jni_md.h for non-Android
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    include_directories(
        ${JAVA_INCLUDE_PATH}
        ${JAVA_INCLUDE_PATH2}
)
endif()

include_directories(
    ${LITE_CORE_ROOT}/C/include/
    ${LITE_CORE_ROOT}/LiteCore/Storage/
    ${LITE_CORE_ROOT}/LiteCore/Support/
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Mutable
    ${LITE_CORE_ROOT}/vendor/fleece/API
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Core
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Mutable
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Support
    ${LITE_CORE_ROOT}/vendor/SQLiteCpp/include
    ${LITE_CORE_ROOT}/vendor/SQLiteCpp/sqlite3
    ${LITE_CORE_ROOT}/vendor/sqlite3-unicodesn
    ${LITE_CORE_ROOT}/vendor/BLIP-Cpp/include/blip_cpp
    ${LITE_CORE_ROOT}/vendor/BLIP-Cpp/src/util
    ${LITE_CORE_ROOT}/vendor/civetweb/include
    ${LITE_CORE_ROOT}/vendor/mbedtls/include)

if(BUILD_ENTERPRISE)
    add_definitions(-DCOUCHBASE_ENTERPRISE      # Tells LiteCore it's an EE build
                    -DSQLITE_HAS_CODEC)         # Enables SQLite encryption extension (SEE)
endif()

aux_source_directory(./src/main/cpp  JNI_SRC)
if (NOT REST)
    list(REMOVE_ITEM JNI_SRC "./src/main/cpp/native_c4listener.cc")
endif()
add_library(LiteCoreJNI SHARED ${JNI_SRC})

# set dependency
add_dependencies(LiteCoreJNI JNIHeaders)

add_subdirectory(${LITE_CORE_ROOT} ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    target_link_libraries(  LiteCoreJNI
                            LiteCoreStatic
                            LiteCoreREST_Static
                            BLIPStatic
                            FleeceStatic
                            Support
                            SQLite3_UnicodeSN
                            CivetWeb
                            mbedcrypto)
    if (REST)
        target_link_libraries(  LiteCoreJNI 
                                LiteCoreREST_Static)
    endif()
else()
    
    #find_library(log-lib log)

    target_link_libraries(  LiteCoreJNI
                            LiteCoreStatic
                            BLIPStatic
                            FleeceStatic
                            Support
                            SQLite3_UnicodeSN
                            CivetWeb
                            mbedcrypto
                            "atomic"
                            "log"
                            zlibstatic)
    if (REST)
        target_link_libraries(  LiteCoreJNI 
                                LiteCoreREST_Static)
    endif()
endif()
