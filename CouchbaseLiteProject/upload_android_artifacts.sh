
# make sure we have the following env variables defined
: ${UPLOAD_USERNAME:?"Need to set UPLOAD_USERNAME"}
: ${UPLOAD_PASSWORD:?"Need to set UPLOAD_PASSWORD"}
: ${UPLOAD_VERSION_CBLITE:?"Need to set UPLOAD_VERSION_CBLITE"}
: ${UPLOAD_VERSION_CBLITE_EKTORP:?"Need to set UPLOAD_VERSION_CBLITE_EKTORP"}

./gradlew uploadArchives

