
## CBLiteListener

This provides a webserver wrapper around Couchbase-Lite so that it can be called via HTTP REST calls.

## How to add to your project

Eventually this will be a matter of specifiying a maven repo url's and adding a few entries to your builld.gradle file to specify the dependencies.

In it's current state, this is not easy because the dependent jars are not in a central repository.  But I think the following should work:

- In the top level project, build the project as you normally would (eg, `./gradlew build` or something that calls it)

- Find the CBLiteListener.aar file under the `build` directory

- Unzip CBLiteListener.aar and get the .jar file and dependent .jar files

- Copy these .jar files into your project's lib directory

- Specify them as local dependencies in your build.gradle file

