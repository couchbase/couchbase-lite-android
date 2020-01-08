#
# Run automated tests
#
GROUP='com.couchbase.lite'
PRODUCT='couchbase-lite-android'
EDITION='community'


function usage() {
    echo "Usage: $0 <build number> <reports path>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

BUILD_NUMBER="$1"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

REPORTS="$2"
if [ -z "REPORTS" ]; then
    usage
fi

echo "======== Run automated tests"
./gradlew connectedAndroidTest -PtargetAbis=armeabi-v7a --console=plain --info || exit 1

echo "======== Copy test reports ========"
cp -rp lib/build/reports/* "${REPORTS}/"

echo "======== TEST COMPLETE"
