[![Gitter](https://badges.gitter.im/couchbase/couchbase-lite-java-core.svg)](https://gitter.im/couchbase/couchbase-lite-java-core?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

Couchbase-Lite-Android is a lightweight embedded NoSQL database engine for Android with the built-in ability to sync to Couchbase Server on the backend.  

It is the Android port of [Couchbase Lite iOS](https://github.com/couchbase/couchbase-lite-ios).    

## Architecture

![](http://tleyden-misc.s3.amazonaws.com/couchbase-lite/couchbase-lite-architecture.png)

Couchbase Lite databases are able to sync with each other via [Sync Gateway](https://github.com/couchbase/sync_gateway/) backed by [Couchbase Server](http://www.couchbase.com/couchbase-server/overview).

Other ways to setup replication:

* No replication -- just local data store.
* Peer-to-peer repolication between Couchbase Lite instances.
* Replication to multiple Sync Gateway instances rather than a single Sync Gateway.
* Etc ..

## Documentation Overview

* This [README](https://github.com/couchbase/couchbase-lite-android/blob/master/README.md)
* [Official Documentation](http://developer.couchbase.com/mobile/develop/guides/couchbase-lite/index.html) for the latest release
* [Javadocs](http://factory.couchbase.com/view/build/view/mobile_dev/view/android/job/build_cblite_android_100/74/artifact/cblite_android_javadocs_1.0.0-74.zip) for [1.0](https://github.com/couchbase/couchbase-lite-android/tree/1.0.0) release
* [Wiki](https://github.com/couchbase/couchbase-lite-android/wiki)

## Getting started with Couchbase Lite

* Download and run the [GrocerySync](https://github.com/couchbaselabs/GrocerySync-Android) and [TodoLite](https://github.com/couchbaselabs/ToDoLite-Android) demo applications.

* Create your own Hello World Couchbase Lite via the [Getting Started](http://developer.couchbase.com/mobile/develop/training/build-first-android-app/index.html) guide.  *(warning: these Getting Started guide correspond to the 1.0.0 release of Couchbase Lite, so you may run into issues with later releases.)*

## Adding Couchbase Lite to your Gradle project

Using Gradle is the easiest way to automate Couchbase Lite builds in your project.

**Important note**: Maven artifacts can only be included with **gradle** builds, since the **mvn** tool does not know how to resolve and build `.aar` dependencies.  

### Using latest official release

##### Optional: Maven repo URL: `http://files.couchbase.com/maven2/`

NOTE: From 1.1.0 releae, Couchbase Lite Android is also hosted on JCenter which is default maven repository for Android Studio.

In the project level `build.gradle` file, specify maven repo URL.
```
repositories {
    jcenter()
    maven {
        url "http://files.couchbase.com/maven2/"
    }
}
```

##### Workaround for "duplicate files during packaging of APK" issue

In the application level `build.gradle` file, add following in `android` section
```
// workaround for "duplicate files during packaging of APK" issue
// see https://groups.google.com/d/msg/adt-dev/bl5Rc4Szpzg/wC8cylTWuIEJ
packagingOptions {
    exclude 'META-INF/ASL2.0'
    exclude 'META-INF/LICENSE'
    exclude 'META-INF/NOTICE'
}    
```

##### Set couchbase-lite-android as dependency

In the application level `build.gradle` file, add following in `dependencies` section
```
compile 'com.couchbase.lite:couchbase-lite-android:{latest-version}'
```

In case of using couchbase lite 1.2.1
```
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.android.support:appcompat-v7:21.0.3'
    compile 'com.couchbase.lite:couchbase-lite-android:1.2.1'
}
```


Where ${latest_version} should be replaced by something that looks like `1.1.1`.  To find the latest version, check our [Maven Repo](http://files.couchbase.com/maven2/com/couchbase/lite/couchbase-lite-android/) directly and look for the latest version, ignoring anything that has a dash after it.  (Eg, ignore items like `1.0.3-239` because they aren't official releases).

### Using master branch version (bleeding edge)

Maven repo URL: `http://files.couchbase.com/maven2/`

```
compile 'com.couchbase.lite:couchbase-lite-android:0.0.0-517'
```

While `0.0.0-517` was the latest build at the time of writing, it's probably out of date by the time you are reading this. To get the latest build number (eg, the "517" part of the version above), see our [Maven Repo](http://files.couchbase.com/maven2/com/couchbase/lite/couchbase-lite-android/) and look for the highest numbered version that starts with `0.0.0-` and is later than `0.0.0-517`

Here is a [complete gradle file](https://github.com/couchbaselabs/GrocerySync-Android/blob/master/GrocerySync-Android/build.gradle) that uses this maven artifact.


### Zipfile that includes jars

For Eclipse and Phonegap users, here are links to the zip file which includes the jars:

* [Master Branch build #515 zipfile](http://mobile.jenkins.couchbase.com/job/couchbase-lite-android-create-zip/37/artifact/artifacts/couchbase-lite-0.0.0-515-android_community.zip) - to get more recent builds, see [Jenkins CI builds](http://mobile.jenkins.couchbase.com/job/couchbase-lite-android-create-zip/)
* To get the latest released zipfile, go to [the official download site](http://www.couchbase.com/download#cb-mobile) and download the latest release.


## Building Couchbase Lite via Android Studio

If you just want the pre-built binaries, see instructions above.  The instructions that follow explain how to build Couchbase Lite from source.


### Android Studio compatibility table

These are known working versions.  Other versions might be compatible (eg, later versions are likely to be compatible)

Couchbase Lite Version  | Android Studio Version
------------- | -------------
1.0.0  | Android Studio 0.5.7
1.0.1  | Android Studio 0.5.7
1.0.2  | Android Studio 0.8.2
1.0.3.x | Android Studio 0.8.2 - 0.8.9
1.0.4  | Android Studio 1.0
Master | Android Studio 1.0

### Prerequisites

* [Download Android Studio](http://developer.android.com/sdk/installing/studio.html).  

* Under Tools / Android / Android SDK Manager make sure "Extras/Google Repository" and "Extras/Android Support Repository" are installed.

### Clone the git repository

Use Git to clone the Couchbase Lite repository to your local disk: 

```
$ git clone git://github.com/couchbase/couchbase-lite-android.git
$ cd couchbase-lite-android
$ git submodule update --init --recursive
```

### Enable settings.gradle file

* `cp settings.gradle.example settings.gradle`

*Note: settings.gradle cannot be checked in directly due to Android Studio issue #[65915](https://code.google.com/p/android/issues/detail?id=65915)*

### Importing Project into Android Studio

You should be able to import the project directly into Android Studio:

* Start Android Studio
* Choose File / Import Project and choose the settings.gradle file in the couchbase-lite-android directory you cloned earlier.  (Note: if you are on the welcome screen, choose "Import Non-Android Studio Project)
* Hit Finish and wait for all tasks to finish (may take a while)

Caveat: when importing, you may see [Wrong offset: 290. Should be in range: 0, 230](https://code.google.com/p/android/issues/detail?id=74673), but after that you should be able to click the menu bar item "Sync Project with Gradle files" and the project should work.

### Running tests in Android Studio


See [Running unit tests for couchbase lite android](https://github.com/couchbase/couchbase-lite-android/wiki/Running-unit-tests-for-couchbase-lite-android) 

### Running project in Android Studio

If you've checked out this project directly, you might notice there is *nothing to run*.  That is correct, as this project is a library.

If you want to run something (aside from the tests), you should get one of the sample apps listed below.


## Building Couchbase Lite on command line via gradle

* Clone the git repository
    * See details above
* Enable settings.gradle file
    * See details above
* If you don't already have a `local.properties` file, configure Android Studio SDK location
    * `cp local.properties.example local.properties`
    * Customize `local.properties` according to your SDK installation directory
* Compile (assemble)
    * `$ ./gradlew assemble`
* Test
    * `$ ./gradlew connectedAndroidTest`
* Build (compile & test)
    * `$ ./gradlew build`

## Example Apps

* [GrocerySync](https://github.com/couchbaselabs/GrocerySync-Android)  
    * Simplest example
* [TodoLite](https://github.com/couchbaselabs/ToDoLite-Android)
    * Facebook auth
    * Replication with channels
    * Image attachments
    * Ability for users to share data
* [LiteServAndroid](https://github.com/couchbaselabs/couchbase-lite-android-liteserv)
    * REST API example
* [TestCouchLiteAndroid](https://github.com/iraycd/TestCouchLiteAndroid)
    * Simple example contributed by [@iraycd](https://github.com/iraycd) 

## System Requirements

- Android 2.3 Gingerbread (API level 9) and above.

## Limitations

- Docs are limited to 2MB - see [issue 357](https://github.com/couchbase/couchbase-lite-android/issues/357)
- Attachments are limited to 20MB if using Sync Gateway

## Getting Help

* [Couchbase Mobile Google Group](http://groups.google.com/group/mobile-couchbase/)
* [File a github issue](https://github.com/couchbase/couchbase-lite-android/issues) -- please follow the [bug reporting guidelines](https://github.com/couchbase/mobile/wiki/Reporting-Bugs)

## Credits

[Credits](https://github.com/couchbase/couchbase-lite-android/wiki/Credits)

## License
- Apache License 2.0

