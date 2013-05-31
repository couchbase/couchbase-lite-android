# Couchbase-Lite-Android #

by Marty Schoch (marty@couchbase.com) + Traun Leyden (tleyden@couchbase.com)

**Couchbase-Lite-Android** is the Android port of [Couchbase Lite iOS](https://github.com/couchbase/couchbase-lite-ios).  

For information on the high-level goals of the project see the [Couchbase Lite iOS Readme](https://github.com/couchbase/couchbase-lite-ios/blob/master/README.md).  This document will limit itself to Android specific issues and deviations from the iOS version.

## Prerequisites

* [Download Android Studio](http://developer.android.com/sdk/installing/studio.html) 

* Under Tools / Android / Android SDK Manager and install "Extras/Google Repository" and "Extras/Android Support Repository"


## Getting the Code

Use Git to clone the Couchbase Lite repository to your local disk: 

```
$ git clone git://github.com/couchbase/couchbase-lite-android.git
```

## Running tests

The tests require one of the following to be installed and running:

* CouchDB (recommended v1.3, but earlier versions will probably work)
* Sync-Gateway

### Configure local-test.properties to point to database

First copy the test.properties -> local-test.properties, eg:

```
$ cd CBLite/src/instrumentTest/assets/
$ cp test.properties local-test.properties 
```

Now customize local-test.properties to point to your database (URL and db name)

_Note:_ you will need to repeat the above step for the other library projects (eg, CBLiteEktorp, etc)

### Modify build.gradle to use a different dependencies file

In order to workaround [this gradle/ADT issue](https://groups.google.com/forum/?fromgroups#!topic/adt-dev/Efpf87EoDQ0), you will need to change your build.gradle file from:

```
apply from: 'dependencies.gradle'            
// apply from: 'dependencies-test.gradle'
```

to

```
// apply from: 'dependencies.gradle'  
apply from: 'dependencies-test.gradle' 
```

### Tell gradle to run tests

```
$ ./gradlew clean && ./gradlew connectedInstrumentTest
```

_Warning:_ the full test suite takes a _long time_ (10mins?) because of some 1 minute waits in the Ektorp tests.  You can tell it to just run the core tests (which are much faster) by running `./gradlew :CBLite:clean && ./gradlew :CBLite:connectedInstrumentTest` instead.

_Note:_ there's not yet a known way to run the tests via Android Studio.  At the time of writing, was not yet supported by Android Studio.
 
## Building and deploying an archive

Warning: this is a complicated process due to the issues mentioned on [this thread](https://groups.google.com/forum/#!topic/adt-dev/H2Jk2rVs6G8)


Preparation:

- You will need to have a maven repository installed and running
- Delete all three archives from your repo: CBLite, CBLiteEktorp, and CBLiteJavascript
- rm -rf ~/.gradle to delete any gradle cache files

Building and deploying:

- In the build.gradle file for CBLite, CBLiteEktorp, and CBLiteJavascript, set apply from: 'dependencies.gradle'
- Run `./gradlew clean && ./gradlew :CBLite:uploadArchives`
- Modify CBLiteEktorp/build.gradle to set apply from: 'dependencies-archive.gradle'
- Run `./gradlew clean && ./gradlew :CBLiteEktorp:uploadArchives`
- Modify CBLiteJavacript/build.gradle to set apply from: 'dependencies-archive.gradle'
- Run `./gradlew clean && ./gradlew :CBLiteJavascript:uploadArchives`


The end result: you will have three .aar archives in your maven repository.


## Wiki

See the [wiki](https://github.com/couchbase/couchbase-lite-android/wiki)

## Example Apps

* [GrocerySync](https://github.com/couchbaselabs/GrocerySync-Android)

## Current Status
- Ported core functionality present in Couchbase-Lite-iOS as of approximately 1 year ago (May 2012).
- Unit tests pass

## Requirements
- Android 2.2 or newer
- Jackson JSON Parser/Generator

## License
- Apache License 2.0

## Known Issues
- Cannot deal with large attachments without running out of memory.
- If the device goes offline, replications will stop and will not be automatically restarted.
- Exception Handling in the current implementation makes things less readable.  This was a deliberate decision that was made to make it more of a literal port of the iOS version.  Once the majority of code is in place and working I would like to revisit this and handle exceptions in more natural Android/Java way.

## TODO
- Finish porting all of TDRouter so that all operations are supported

