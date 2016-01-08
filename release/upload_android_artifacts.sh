#!/bin/sh
: ${MAVEN_UPLOAD_VERSION:?"Need to set 'MAVEN_UPLOAD_VERSION' non-empty"}
: ${MAVEN_UPLOAD_USERNAME:?"Need to set MAVEN_UPLOAD_USERNAME non-empty"}
: ${MAVEN_UPLOAD_PASSWORD:?"Need to set MAVEN_UPLOAD_PASSWORD non-empty"}
: ${MAVEN_UPLOAD_REPO_URL:?"Need to set MAVEN_UPLOAD_REPO_URL non-empty"}

./gradlew :libraries:couchbase-lite-java-core:build :libraries:couchbase-lite-java-forestdb:build :libraries:couchbase-lite-java-native:sqlcipher:build :libraries:couchbase-lite-java-native:sql-custom:build :libraries:couchbase-lite-java-native:sql-default:build &&
./gradlew :libraries:couchbase-lite-java-core:uploadArchivesWrapper :libraries:couchbase-lite-java-forestdb:uploadArchivesWrapper :libraries:couchbase-lite-java-native:sqlcipher:uploadArchivesWrapper :libraries:couchbase-lite-java-native:sql-custom:uploadArchivesWrapper :libraries:couchbase-lite-java-native:sql-default:uploadArchivesWrapper


 wget http://files.couchbase.com/maven2/com/couchbase/lite/couchbase-lite-android-forestdb/${MAVEN_UPLOAD_VERSION}/couchbase-lite-android-forestdb-${MAVEN_UPLOAD_VERSION}.pom
 sed -i -e "s/couchbase-lite-android.libraries/com.couchbase.lite/" couchbase-lite-android-forestdb-${MAVEN_UPLOAD_VERSION}.pom
 curl -v -u ${MAVEN_UPLOAD_USERNAME}:${MAVEN_UPLOAD_PASSWORD} --upload-file couchbase-lite-android-forestdb-${MAVEN_UPLOAD_VERSION}.pom ${MAVEN_UPLOAD_REPO_URL}/com/couchbase/lite/couchbase-lite-android-forestdb/${MAVEN_UPLOAD_VERSION}/couchbase-lite-android-forestdb-${MAVEN_UPLOAD_VERSION}.pom

rm -rf ~/.m2/repository/com/couchbase/lite/*

./gradlew build -DbuildAndroidWithArtifacts && ./gradlew uploadArchivesWrapper -DbuildAndroidWithArtifacts
