## How to build JNI libraries

Currently there is very limited support for building multiple native libraries by using gradle. Please follow the steps below to build the JNI libraries.

1. Download the NDK tool from the [Android NDK Document](https://developer.android.com/tools/sdk/ndk/index.html).

2. Unpack the downloaded NDK tool and move it to your desired location. You may add its location to your PATH enviroment.

3. `$ cd <couchbase-lite-android>/src/main`

4. `$ ndk-build`

5. `$ mv libs\* <couchbase-lite-android>/jniLibs` (Move all compiled binaries in place)

6. `$ rm -rf libs obj` (Clean up)

