
The only reason the following libraries are here:

- jackson-core-asl-1.9.2.jar
- jackson-mapper-asl-1.9.2.jar
- rhino-1.7R3.jar

are because of the need to do a workaround to this issue: https://groups.google.com/forum/?fromgroups#!topic/adt-dev/Efpf87EoDQ0

After this issue is fixed, and the tests can use the maven dependencies, these should be removed.