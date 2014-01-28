#!/bin/bash

THIS_DIR=`dirname $0`

pushd ${THIS_DIR}  2>&1 > /dev/null

mvn --settings ./settings.xml --quiet clean package

popd               2>&1 > /dev/null
