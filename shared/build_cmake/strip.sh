#!/bin/bash

PREFIX=""
if [[ $# > 0 ]]; then
  PREFIX="${1}"
fi

echo "-s" > stripopts
while read line; do
  if [[ "$line" != "" && "${line:0:1}" != "#" ]]; then
    echo "-K ${line:1}" >> stripopts
  fi
done < ../../C/c4.exp

echo "libLiteCore.so" >> stripopts

COMMAND="${PREFIX}strip @stripopts"
eval ${COMMAND}
rm stripopts
