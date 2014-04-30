# Couchbase-Lite-Android #

Couchbase-Lite-Android is a lightweight embedded NoSQL database engine for Android with the built-in ability to sync to Couchbase Server on the backend.  

It is the Android port of [Couchbase Lite iOS](https://github.com/couchbase/couchbase-lite-ios).  

## Architecture

![](http://tleyden-misc.s3.amazonaws.com/couchbase-lite/couchbase-lite-architecture.png)

Couchbase Lite databases are able to sync with eachother via [Sync Gateway](https://github.com/couchbase/sync_gateway/) backed by [Couchbase Server](http://www.couchbase.com/couchbase-server/overview)


## Documentation Overview

* This [README](https://github.com/couchbase/couchbase-lite-android/blob/master/README.md)
* [Official Documentation](http://docs.couchbase.com/couchbase-lite/cbl-android/) for [beta2](https://github.com/couchbase/couchbase-lite-android/blob/1.0-beta2) release
* [Javadocs](http://www.couchbase.com/autodocs/couchbase-lite-android-1.0b2/index.html) for [beta2](https://github.com/couchbase/couchbase-lite-android/blob/1.0-beta2) release
* [Wiki](https://github.com/couchbase/couchbase-lite-android/wiki)

## Getting started with Couchbase Lite

* Download and run the [GrocerySync](https://github.com/couchbaselabs/GrocerySync-Android) demo application

* Create your own Hello World Couchbase Lite via the [Getting Started](https://github.com/couchbase/couchbase-lite-android/wiki/Getting-Started) guide.


## Getting the pre-built jars / maven artifacts

### Maven master branch

Maven repo URL: `http://files.couchbase.com/maven2/`

```
<dependency>
  <groupId>com.couchbase.lite</groupId>
  <artifactId>android</artifactId>
  <version>0.0.0-272</version>
</dependency>
```

To get the latest build number (eg, the "272" part of the version above), see [Jenkins CI builds](http://factory.couchbase.com/view/build/view/mobile_dev/view/android/job/build_cblite_android_master/)

To see an example gradle configuration, see [GrocerySync's build.gradle file](https://github.com/couchbaselabs/GrocerySync-Android/blob/master/GrocerySync-Android/build.gradle)

### Maven beta2 release

Maven repo URL: `http://files.couchbase.com/maven2/`

```
<dependency>
  <groupId>com.couchbase.cblite</groupId>
  <artifactId>CBLite</artifactId>
  <version>1.0.0-beta2</version>
</dependency>
```

### Zipfile that includes jars

For Eclipse and Phonegap users, here are links to the zip file which includes the jars:

* [Master Branch build #272 zipfile](http://factory.couchbase.com/view/build/view/mobile_dev/view/android/job/build_cblite_android_master/lastSuccessfulBuild/artifact/couchbase-lite-0.0.0-272-android-community.zip) - to get more recent builds, see [Jenkins CI builds](http://factory.couchbase.com/view/build/view/mobile_dev/view/android/job/build_cblite_android_master/)
* [Beta2 zipfile](http://packages.couchbase.com/releases/couchbase-lite/android/1.0-beta/couchbase-lite-community-android_1.0-beta2.zip)


## Building Couchbase Lite from source

### Prerequisites

* [Download Android Studio](http://developer.android.com/sdk/installing/studio.html) 

  * If you are using the beta2 release or stable branch of Couchbase Lite, use the latest version in the stable channel (currently Android Studio 0.3.X)

  * If you are using the master branch of Couchbase Lite, use the latest version in the canary channel (currently Android Studio 0.4.3)

* Under Tools / Android / Android SDK Manager and install "Extras/Google Repository" and "Extras/Android Support Repository" (future versions of Android Studio may make this step unnecessary)

**Note** recent versions after Android Studio 0.4.3 are not able to import the project due to [Issue #65915](https://code.google.com/p/android/issues/detail?id=65915), so it's recommended to use Android Studio 0.4.3.

### Clone the git repository

Use Git to clone the Couchbase Lite repository to your local disk: 

```
$ git clone git://github.com/couchbase/couchbase-lite-android.git
cd couchbase-lite-android
$ git submodule init && git submodule update
```

### Configure Android Studio SDK location

* `cp local.properties.example local.properties`
* Customize `local.properties` according to your SDK installation directory


### Importing Project into Android Studio

You should be able to import the project directly into Android Studio:

* Start Android Studio
* Choose File / Import and choose the couchbase-lite-android/CouchbaseLiteProject directory [screenshot](http://cl.ly/image/1d0w0J0H0x1u)
* Choose Import from External Model and make sure Gradle is selected [screenshot](http://cl.ly/image/2Y1m0O3U1Q2I)
* Check the *auto-import* and the *Use gradle wrapper (recommended)* checkboxes [screenshot](http://cl.ly/image/1I0r1x2J032i)
* Hit Finish and wait for all tasks to finish (may take a while)

After it's finished with the import, it should look [like this](http://cl.ly/image/3R3X0Q3o1H09)

### Running tests

There are two wiki pages which describe how to run the tests:

* [Running unit tests for couchbase lite android](https://github.com/couchbase/couchbase-lite-android/wiki/Running-unit-tests-for-couchbase-lite-android)  (newer)

* [Running the Test Suite](https://github.com/couchbase/couchbase-lite-android/wiki/Running-the-test-suite) wiki page.

## Example Apps

* [TodoLite](https://github.com/couchbaselabs/ToDoLite-Android)
* [GrocerySync](https://github.com/couchbaselabs/GrocerySync-Android)  
* [LiteServAndroid](https://github.com/couchbaselabs/couchbase-lite-android-liteserv)
* [CouchChatAndroid](https://github.com/couchbaselabs/CouchChatAndroid) -- just a stub at this point.

## Project Structure

* [Project Structure](https://github.com/couchbase/couchbase-lite-android/wiki/Project-structure) wiki page that describes the new project structure.
* A [mailing list post](https://groups.google.com/forum/#!topic/mobile-couchbase/Zsn8TG5F88o) describing the project structre

## Requirements

- Android 2.3 Gingerbread (API level 9) and above.

## Credits

[Credits](https://github.com/couchbase/couchbase-lite-android/wiki/Credits)

## License
- Apache License 2.0

[![githalytics.com alpha](https://cruel-carlota.pagodabox.com/bc53967fe3191ba75b4a62c9372d9928 "githalytics.com")](http://githalytics.com/couchbase/couchbase-lite-android)
