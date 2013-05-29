
The only reason the following libraries are here:

- org.ektorp-1.2.2.jar
- org.ektorp.android-1.2.2.jar
- jackson-core-asl-1.9.2.jar
- jackson-mapper-asl-1.9.2.jar
- slf4j-api-1.6.1.jar 
- slf4j-jdk14-1.6.1.jar
- commons-io-2.0.1.jar

are because of the need to do a workaround to this issue: https://groups.google.com/forum/?fromgroups#!topic/adt-dev/Efpf87EoDQ0

After this issue is fixed, and the tests can use the maven dependencies, these should be removed.