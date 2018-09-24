Spinnaker Application & Project Metadata Repository
------------------------------------
[![Build Status](https://api.travis-ci.org/spinnaker/front50.svg?branch=master)](https://travis-ci.org/spinnaker/front50)
This service fronts a Spinnaker datastore. By default it's Cassandra, however, it's intended that any datastore could work. Front50 written using [Spring Boot][0]. 

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8180.  The JVM will _not_ wait for
the debugger to be attached before starting Front50; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

[0]:http://projects.spring.io/spring-boot/


### Modular builds

By default, Front50 is built with all storage providers included. To build only a subset of 
providers, use the `includeProviders` flag:

```
./gradlew -PincludeProviders=s3,gcs clean build
```

You can view the list of all providers in `gradle.properties`.
