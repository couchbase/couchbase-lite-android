#!/usr/bin/env bash
#
# Run automated tests
#

GROUP='com.couchbase.lite'
PRODUCT='coucbase-lite-android'
EDITION='community'

function usage() {
    echo "Usage: $0 <build number> <reports path>"
    exit 1
}

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

REPORTS="$2"
if [ -z "REPORTS" ]; then
    usage
fi

echo "======== Run automated tests"
./gradlew ciTest --info --console=plain -PautomatedTests=true -PbuildNumber=${BUILD_NUMBER} || exit 1

echo "======== Copy test reports ========"
cp -rp lib/build/reports/* "${REPORTS}/"

echo "======== TEST COMPLETE"
