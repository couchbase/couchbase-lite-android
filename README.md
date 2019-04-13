
# Couchbase Lite 2.0

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Build Status](https://travis-ci.org/couchbase/couchbase-lite-android.svg?branch=master)](https://travis-ci.org/couchbase/couchbase-lite-android)

**Couchbase Lite** is an embedded lightweight, document-oriented (NoSQL), syncable database engine.

Couchbase Lite 2.0 has a completely new set of APIs. The implementation is on top of [Couchbase Lite Core](https://github.com/couchbase/couchbase-lite-core), which is also a new cross-platform implementation of database CRUD and query features, as well as document versioning.

## Requirements

- Android 4.4+ (API 19+)
- Supported architectures: armeabi-v7a, arm64-v8a and x86
- Android Studio 3.+

## Installation

Download the latest AAR or grab via Maven

### Download
- https://www.couchbase.com/downloads

### Gradle
Add the following in the dependencies section of the application's build.gradle (the one in the app folder).

```
dependencies {
    implementation 'com.couchbase.lite:couchbase-lite-android:2.0.0'
}
```

### Maven
```
<dependency>
  <groupId>com.couchbase.lite</groupId>
  <artifactId>couchbase-lite-android</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Documentation

- [Developer Guide](https://developer.couchbase.com/documentation/mobile/2.0/couchbase-lite/java.html)

## How to build from source

**THIS MODULE CAN NO LONGER BE BUILT BY ITSELF**
To build couchbase lite, clone https://github.com/couchbase/couchbase-lite-android-ce.git

## Sample Apps

- [Todo](https://github.com/couchbaselabs/mobile-training-todo/tree/feature/2.0)

## ProGuard
If you are using ProGuard you might need to add the following options:
```
# OkHttp3
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# CBL2.x
-keep class com.couchbase.litecore.**{ *; }
-keep class com.couchbase.lite.**{ *; }

```

## License

Apache 2 [license](LICENSE).

