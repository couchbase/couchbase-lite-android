#!/bin/bash

set -e
THIS_DIR=`dirname $0`


MVNREPO=http://files.couchbase.com/maven2
REPO_ID=couchbase.public.repo

#    UPLOAD_USERNAME=...either set these environment variables...
#    UPLOAD_PASSWORD=...or change this script to ask for input...

UPLOAD_CREDENTS=${UPLOAD_USERNAME}:${UPLOAD_PASSWORD}

JARS="servlet webserver"
VRSN=2-3

if [[ ! -e ~/.m2/settings-security.xml ]]
  then
    echo cat ${THIS_DIR}/settings-security.xml >> ~/.m2/settings-security.xml
         cat ${THIS_DIR}/settings-security.xml >> ~/.m2/settings-security.xml
fi

#### you will have to create the directories in the repo first, using:
#  
#  curl --user ${UPLOAD_CREDENTS} -XMKCOL http://files.couchbase.com/maven2/com/couchbase/cblite/${J}/
#  curl --user ${UPLOAD_CREDENTS} -XMKCOL http://files.couchbase.com/maven2/com/couchbase/cblite/${J}/${VRSN}/


for J in ${JARS}
  do
    JARFILE=${J}-${VRSN}.jar
    if [[ ! -e ${JARFILE} ]]
      then
        echo -e "\n\nNO SUCH FILE: ${JARFILE}\n\ncopy it to `pwd`"
        echo -e "from       https://github.com/couchbase/couchbase-lite-android-listener/libs-src/WebServer-1105.zip\n\n"
      else
        echo "UPLOADING ${J} to .... maven repo:  ${MVNREPO}"
        mvn --settings ./settings.xml -X     \
            deploy:deploy-file               \
            -Durl=${MVNREPO}                 \
            -DrepositoryId=${REPO_ID}        \
            -Dfile=${JARFILE}                \
            -DuniqueVersion=false            \
            -DgeneratePom=true               \
            -DgroupId=com.couchbase.cblite   \
            -DartifactId=${J}                \
            -Dversion=${VRSN}                \
            -Dpackaging=jar                   
    fi
  done

