#!/bin/sh
: ${MAVEN_UPLOAD_VERSION:?"Need to set 'MAVEN_UPLOAD_VERSION' non-empty"}
: ${MAVEN_UPLOAD_USERNAME:?"Need to set MAVEN_UPLOAD_USERNAME non-empty"}
: ${MAVEN_UPLOAD_PASSWORD:?"Need to set MAVEN_UPLOAD_PASSWORD non-empty"}
: ${MAVEN_UPLOAD_REPO_URL:?"Need to set MAVEN_UPLOAD_REPO_URL non-empty"}

#at first build all projects
./gradlew :libraries:couchbase-lite-java-core:build && ./gradlew :build -DbuildAndroidWithArtifacts &&
#then upload artifacts
./gradlew :libraries:couchbase-lite-java-core:uploadArchivesWrapper && ./gradlew :uploadArchivesWrapper -DbuildAndroidWithArtifacts
