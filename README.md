# Couchbase-Lite-Android #

by Marty Schoch (marty@couchbase.com) + Traun Leyden (tleyden@couchbase.com)

**Couchbase-Lite-Android** is the Android port of [Couchbase Lite iOS](https://github.com/couchbase/couchbase-lite-ios).  

For information on the high-level goals of the project see the [Couchbase Lite iOS Readme](https://github.com/couchbase/couchbase-lite-ios/blob/master/README.md).  This document will limit itself to Android specific issues and deviations from the iOS version.

## Getting Started

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
- Exception Handling in the current implementation makes things less readable.  This was a deliberate decision that was made to make it more of a literal port of the iOS version.  Once the majority of code is in place and working I would like to revisit this and handle exceptions in more natural Android/Java way.

## TODO
- Finish porting all of TDRouter so that all operations are supported

