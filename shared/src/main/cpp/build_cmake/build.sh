#!/bin/bash

# Check NDK
if [ -z "$ANDROID_NDK_ROOT" ]; then
    echo "Need to set ANDROID_NDK_ROOT"
    exit 1
else
	if [ ! -d "$ANDROID_NDK_ROOT" ]; then
	    echo "The directory $ANDROID_NDK_ROOT does not exist"
	    exit 1
	fi	
fi


# First argument is abi type (armeabi-v7a, arm64-v8a, x86)
# Second argument is system API version
# Third argument is build type Release or Debug

ARCH_ABI=$1
API_VERSION=$2
BUILD_TYPE=$3

cmake -DCMAKE_SYSTEM_NAME=Android \
-DCMAKE_ANDROID_NDK=$ANDROID_NDK_ROOT \
-DCMAKE_ANDROID_STL_TYPE=c++_static \
-DCMAKE_ANDROID_ARCH_ABI="$ARCH_ABI" \
-DCMAKE_ANDROID_NDK_TOOLCHAIN_VERSION=clang \
-DCMAKE_SYSTEM_VERSION="$API_VERSION" \
-DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
- G "Unix Makefiles" \
../..

make VERBOSE=1 -j4

