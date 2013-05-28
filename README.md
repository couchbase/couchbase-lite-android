# Couchbase-Lite-Android #

by Marty Schoch (marty@couchbase.com) + Traun Leyden (tleyden@couchbase.com)

**Couchbase-Lite-Android** is the Android port of [Couchbase Lite iOS](https://github.com/couchbase/couchbase-lite-ios).  

For information on the high-level goals of the project see the [Couchbase Lite iOS Readme](https://github.com/couchbase/couchbase-lite-ios/blob/master/README.md).  This document will limit itself to Android specific issues and deviations from the iOS version.

## Prerequisites

* [Download Android Studio](http://developer.android.com/sdk/installing/studio.html) 


## Getting the Code

Use Git to clone the Couchbase Lite repository to your local disk: 

```
$ git clone git://github.com/couchbase/couchbase-lite-android.git
```


## Building code via Gradle

```
$ ./gradlew build
```

## Building from Android Studio

Hit the "Run" button (note: this won't actually run anything, but it should build the code)

## Building an archive via Gradle

```
./gradlew clean && ./gradlew uploadArchives
```

## Running tests via Gradle

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

### Run tests via Gradle

```
$ ./gradlew clean && ./gradlew connectedInstrumentTest
```
 

## Wiki

See the [wiki](https://github.com/couchbase/couchbase-lite-android/wiki)

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

