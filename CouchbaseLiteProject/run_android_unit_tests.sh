
# This requires an emulator to be running or it will fail!

# It expects a sync gateway to be running on 127.0.0.1:4984 with 
# full guest access, eg:
#     "users": { "GUEST": {"disabled": false, "admin_channels": ["*"]} }
# Note: the syng gateway url is controlled by these files:
#     ./CBLite/src/instrumentTest/assets/test.properties
#     ./CBLiteEktorp/src/instrumentTest/assets/test.properties 
# which can be overridden by local-test.properties in the same directory
#
# See also https://github.com/couchbase/couchbase-lite-android/wiki/Running-the-test-suite

./gradlew clean && ./gradlew :CBLite:connectedInstrumentTest && ./gradlew :CBLiteJavascript:connectedInstrumentTest
