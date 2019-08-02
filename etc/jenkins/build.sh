#!/usr/bin/env bash
#
# CI Build script for Enterprise Android
#

GROUP='com.couchbase.lite'
PRODUCT='coucbase-lite-android'
EDITION='community'

MAVEN_URL="http://mobile.maven.couchbase.com/maven2/cimaven"

function usage() {
    echo "Usage: $0 <build number> <artifacts path>"
    exit 1
}

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

ARTIFACTS="$2"
if [ -z "$ARTIFACTS" ]; then
    usage
fi

echo "======== Build Couchbase Lite Android, Community Edition v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew ciBuild -PbuildNumber=${BUILD_NUMBER} || exit 1

echo "======== Publish build candidates to CI maven"
./gradlew ciPublish -PbuildNumber=${BUILD_NUMBER} -PmavenUrl=${MAVEN_URL} || exit 1

echo "======== Copy artifacts to staging directory"
cp lib/build/outputs/aar/*.aar ${ARTIFACTS}/
cp lib/build/libs/*.jar ${ARTIFACTS}/
cp lib/build/publications/mavenJava/pom-default.xml  ${ARTIFACTS}/pom.xml

echo "======== BUILD COMPLETE"
