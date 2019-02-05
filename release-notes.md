# Release Notes

## JNI Refactor
The glue code that exposes CB-Lite-Core functionalty to Java has been moved
into this project from it's former home in [couchbase-lite-core](https://github.com/couchbase/couchbase-lite-core). 
Everything that used to be in the directory `$projectRoot/Java` in that project is now in the directory
`$projectRoot/shared/src/main/cpp` in this project.

There are some accompanying changes in the build files.  The most significant of these is that the CMake 
command line parameter `JNI` is obsolete and has been removed.  Source and asset sets have also been modified
to adapt to the new code locations.

## JNI Code re-packaging
In order to clarify what does and does not comprise the Couchbase public API, the JNI classes and their helpers
-- formerly in the package `com.couchbase.litecore` -- have been moved preserving structure,
 to the package `com.couchbase.internal`.  Code in that package or any of its subpackages
is not part of the public API and should not be used in client code.

