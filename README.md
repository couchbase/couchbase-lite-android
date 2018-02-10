
# Couchbase Lite 2.0 (Beta)

**Couchbase Lite** is an embedded lightweight, document-oriented (NoSQL), syncable database engine.

Couchbase Lite 2.0 has a completely new set of APIs. The implementation is on top of [Couchbase Lite Core](https://github.com/couchbase/couchbase-lite-core), which is also a new cross-platform implementation of database CRUD and query features, as well as document versioning.

## Requirements

- Android 4.1+
- Android Studio 3.0

## Installation

1. In the top-level build.gradle file, add the following Maven repository in the allprojects section.

```
allprojects {
    repositories {
        jcenter()
        maven {
            url "http://mobile.maven.couchbase.com/maven2/dev/"
        }
    }
}
```

2. Add the following in the dependencies section of the application's build.gradle (the one in the app folder).

```
dependencies {
    compile 'com.couchbase.lite:couchbase-lite-android:2.0.0-DB022'
}
```

## Documentation

- [Developer Guide](https://developer.couchbase.com/documentation/mobile/2.0/couchbase-lite/java.html)

## Sample Apps

- [Todo](https://github.com/couchbaselabs/mobile-training-todo/tree/feature/2.0)


## License

Like all Couchbase source code, this is released under the Apache 2 [license](LICENSE).

