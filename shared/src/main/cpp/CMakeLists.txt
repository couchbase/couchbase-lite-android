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

set(LITE_CORE_ROOT ${PROJECT_SOURCE_DIR}/../../../../libs/couchbase-lite-core)

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
set(JNI_CLASSPATH  ${PROJECT_SOURCE_DIR}/src)
set(JNI_OUTPUT_DIR ${PROJECT_SOURCE_DIR}/jni)
set(JNI_CLASSES
    com.couchbase.litecore.C4
    com.couchbase.litecore.C4Base
    com.couchbase.litecore.C4BlobKey
    com.couchbase.litecore.C4BlobStore
    com.couchbase.litecore.C4BlobReadStream
    com.couchbase.litecore.C4BlobWriteStream
    com.couchbase.litecore.C4Database
    com.couchbase.litecore.C4DatabaseObserver
    com.couchbase.litecore.C4DocEnumerator
    com.couchbase.litecore.C4Document
    com.couchbase.litecore.C4DocumentObserver
    com.couchbase.litecore.C4FullTextMatch
    com.couchbase.litecore.C4Key
    com.couchbase.litecore.C4Listener
    com.couchbase.litecore.C4Log
    com.couchbase.litecore.C4Prediction
    com.couchbase.litecore.C4Query
    com.couchbase.litecore.C4QueryEnumerator
    com.couchbase.litecore.C4RawDocument
    com.couchbase.litecore.C4Replicator
    com.couchbase.litecore.C4Socket
    com.couchbase.litecore.fleece.AllocSlice
    com.couchbase.litecore.fleece.Encoder
    com.couchbase.litecore.fleece.FLArray
    com.couchbase.litecore.fleece.FLArrayIterator
    com.couchbase.litecore.fleece.FLDict
    com.couchbase.litecore.fleece.FLDictIterator
    com.couchbase.litecore.fleece.FLEncoder
    com.couchbase.litecore.fleece.FLSliceResult
    com.couchbase.litecore.fleece.FLValue
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

aux_source_directory(./jni  JNI_SRC)
if (NOT REST)
    list(REMOVE_ITEM JNI_SRC "./jni/native_c4listener.cc")
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
        target_link_libraries(LiteCoreJNI 
                            LiteCoreREST_Static)
    endif()
else()
    
    #find_library(log-lib log)

	target_link_libraries(	LiteCoreJNI
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
        target_link_libraries(LiteCoreJNI 
                            LiteCoreREST_Static)
    endif()
endif()
