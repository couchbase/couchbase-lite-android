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
- Finish porting all of TDRouter so that all operations are supported

## Running the Demo App (currently requires Eclipse)
1.  Import all 3 projects into Eclipse (TouchDB-Android, TouchDB-Android-Listener, TouchDB-Android-TestApp)
2.  Right-click on TouchDB-Android-TestApp select Properties, select the Android, then in the Library section press Add, select TouchDB-Android, press Add again and select TouchDB-Android-Listener, press OK
3.  Right-click on TouchDB-Android-TestApp select Run As, select Android Application

NOTE: the HTTP Listener uses TDRouter to respond to requests, and TDRouter is still incomplete.  Only a small number of requests are currently supported.
