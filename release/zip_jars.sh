#!/bin/bash

THIS_DIR=`dirname $0`

if [[ ! $1 ]] ; then echo "usage:  $0  build_number (e.g. 0.0.0-1234)   [ log_file ]" ; exit 99 ; fi

LOG='2>&1 | egrep -v '\'\('[0-9]+/[0-9]+K ?'\)\''+'

if [[   $2 ]] ; then  LOG=${LOG}' >> '${2} ; fi


REVISION=$1

pushd ${THIS_DIR}  2>&1 > /dev/null

# echo mvn --settings ./settings.xml --quiet -DREVISION=${REVISION} clean prepare-package package  $LOG | bash
  echo mvn --settings ./settings.xml --debug -DREVISION=${REVISION} clean prepare-package package  $LOG | bash

popd               2>&1 > /dev/null
echo ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ done making android_zipfile
