#
# CI Build script for Enterprise Android
#
GROUP='com.couchbase.lite'
PRODUCT='coucbase-lite-android'
EDITION='community'

MAVEN_URL="http://proget.build.couchbase.com/maven2/cimaven"


function usage() {
    echo "Usage: $0 <sdk path> <build number>"
    exit 1
}

# As of 2.8, the build script takes 3 args.
if [ "$#" -lt 2 ]; then
    usage
fi

SDK_HOME="$1"
if [ -z "$SDK_HOME" ]; then
    usage
fi

BUILD_NUMBER="$2"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

SDK_MGR="${SDK_HOME}/tools/bin/sdkmanager"

echo "======== BUILD Couchbase Lite Android, Community Edition v`cat ../version.txt`-${BUILD_NUMBER}"

echo "======== Install Toolchain"
yes | ${SDK_MGR} --licenses > /dev/null 2>&1
${SDK_MGR} --install 'cmake;3.10.2.4988404'
${SDK_MGR} --install 'ndk;20.0.5594570'

# The Jenkins script has already put passwords into local.properties
cat <<EOF >> local.properties
sdk.dir=${SDK_HOME}
ndk.dir=${SDK_HOME}/ndk/20.0.5594570
cmake.dir=${SDK_HOME}/cmake/3.10.2.4988404
EOF

echo "======== Build"
./gradlew ciCheck -PbuildNumber="${BUILD_NUMBER}" || exit 1

echo "======== Publish artifacts"
./gradlew ciPublish -PbuildNumber="${BUILD_NUMBER}" -PmavenUrl="${MAVEN_URL}" || exit 1

echo "======== BUILD COMPLETE"
