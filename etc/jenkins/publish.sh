#
# Promote the candidate build to an internal release
#
GROUP='com.couchbase.lite'
PRODUCT='couchbase-lite-android'
EDITION='community'

MAVEN_URL="http://proget.build.couchbase.com/maven2/internalmaven"


function usage() {
    echo "Usage: $0 <release version> <build number> <artifacts path> <workspace path>"
    exit 1
}

if [ "$#" -ne 4 ]; then
    usage
fi

VERSION="$1"
if [ -z "$VERSION" ]; then
    usage
fi

BUILD_NUMBER="$2"
if [ -z "$BUILD_NUMBER" ]; then
    usage
fi

ARTIFACTS="$3"
if [ -z "$ARTIFACTS" ]; then
    usage
fi

WORKSPACE="$4"
if [ -z "$WORKSPACE" ]; then
    usage
fi

echo "======== Publish candidate build to internal maven"
./gradlew ciPublish -PbuildNumber=${BUILD_NUMBER} -PmavenUrl=${MAVEN_URL} || exit 1

echo "======== Copy artifacts to staging directory"
POM_FILE='pom.xml'
cp lib/build/outputs/aar/*.aar "${ARTIFACTS}/"
cp lib/build/libs/*.jar "${ARTIFACTS}/"
cp -a lib/build/reports "${ARTIFACTS}"
cp lib/build/publications/mavenJava/pom-default.xml "${ARTIFACTS}/${POM_FILE}"

echo "======== Update package type in pom"
ZIP_BUILD="${WORKSPACE}/zip-build"
rm -rf "${ZIP_BUILD}"
mkdir -p "${ZIP_BUILD}"
pushd "${ZIP_BUILD}"
cp "${ARTIFACTS}/${POM_FILE}" . || exit 1
sed -i.bak "s#<packaging>aar</packaging>#<packaging>pom</packaging>#" "${POM_FILE}" || exit 1
diff "${POM_FILE}" "${POM_FILE}.bak"

echo "======== Fetch library dependencies"
/home/couchbase/jenkins/tools/hudson.tasks.Maven_MavenInstallation/M3/bin/mvn install dependency:copy-dependencies || exit 1
popd

echo "======== Create zip"
ZIP_STAGING="${WORKSPACE}/staging"
rm -rf "${ZIP_STAGING}"
mkdir -p "${ZIP_STAGING}"
pushd "${ZIP_STAGING}"
cp "${ZIP_BUILD}/target/dependency/"*.jar . || exit 1
cp "${WORKSPACE}/product-texts/mobile/couchbase-lite/license/LICENSE_${EDITION}.txt" ./LICENSE.TXT || exit 1
cp "${ARTIFACTS}/${PRODUCT}-${VERSION}-${BUILD_NUMBER}-release.aar" "./${PRODUCT}-${VERSION}.aar" || exit 1
zip -r "${ARTIFACTS}/${PRODUCT}-${VERSION}-android_${EDITION}.zip" * || exit 1
popd

echo "======== PUBLICATION COMPLETE"
