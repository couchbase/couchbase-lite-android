#!/bin/sh

if ! [ -f "settings.gradle" ]
then
	 	echo "settings.gradle not found.";
        exit 11
fi

echo "Building ..."

./gradlew assemble && ./gradlew install

