Spinnaker Application & Project Metadata Repository
------------------------------------
[![Build Status](https://api.travis-ci.org/spinnaker/front50.svg?branch=master)](https://travis-ci.org/spinnaker/front50)
This service fronts a Spinnaker datastore. It's intended that any datastore could work, there are a number of current storage providers. Front50 written using [Spring Boot][0].

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

### Working Locally

The tests are setup to only run if needed services are available.

#### S3
S3 TCK only run if there is a s3 proxy available at 127.0.0.1:9999

This can be provided with the following command:
```bash
docker run -d -p9999:80 \
  --env S3PROXY_AUTHORIZATION="none" \
  --env JCLOUDS_PROVIDER="filesystem" \
  --env JCLOUDS_IDENTITY="remote-identity" \
  --env JCLOUDS_CREDENTIAL="remote-credential" \
  andrewgaul/s3proxy
```

When running the S3 TCK via an IDE make sure to have env `AWS_ACCESS_KEY_ID` and `AWS_SECRET_KEY` set to `null` otherwise the tests will timeout, the gradle test task is already configured this way.
