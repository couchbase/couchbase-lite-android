#
# CI Build script for Enterprise Android
#
GROUP='com.couchbase.lite'
PRODUCT='coucbase-lite-android'
EDITION='community'


function usage() {
    echo "Usage: $0 <build number>"
    exit 1
}

if [ "$#" -ne 1 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

echo "======== Build Couchbase Lite Android, Community Edition v`cat ../version.txt`-${BUILD_NUMBER}"
./gradlew buildCI -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== BUILD COMPLETE"
