# TouchDB-Android #

by Marty Schoch (marty@couchbase.com)

**TouchDB-Android** is the Android port of <a href="https://github.com/couchbaselabs/TouchDB-iOS">TouchDB</a> by Jens Alfke (jens@couchbase.com).  For information on the high-level goals of the project see the <a href="https://github.com/couchbaselabs/TouchDB-iOS/blob/master/README.md">iOS README</a>.  This document will limit itself to Android specific issues and deviations from the iOS version.

## Current Status
- Ported core functionality present in TouchDB-iOS as of Jan 22.
- Unit tests pass

## Requirements
- Android 2.2 or newer
- Jackson JSON Parser/Generator

## License
- Apache License 2.0

## Known Issues
- Exception Handling in the current implementation makes things less readable.  This was a deliberate decision I made to make it more of a literal port of the iOS version.  Once the majority of code is in place and working I would like to revisit this and handle exceptions in more natural Android/Java way.

## TODO
- HTTP Server
