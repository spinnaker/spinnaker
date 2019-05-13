Spinnaker Gateway Service
------------------------------------
[![Build Status](https://api.travis-ci.org/spinnaker/gate.svg?branch=master)](https://travis-ci.org/spinnaker/gate)

This service provides the Spinnaker REST API, servicing scripting clients as well as all actions from [Deck](https://github.com/spinnaker/deck).
The REST API fronts the following services:
* [CloudDriver](https://github.com/spinnaker/clouddriver)
* [Front50](https://github.com/spinnaker/front50)
* [Igor](https://github.com/spinnaker/igor)
* [Orca](https://github.com/spinnaker/orca)

### Modular builds
By default, Gate is built with all authentication providers included. To build only a subset of
providers, use the `includeProviders` flag:
 ```
./gradlew -PincludeProviders=oauth2,x509 clean build
```
 You can view the list of all providers in `gradle.properties`.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:

```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8184.  The JVM will _not_ wait for
the debugger to be attached before starting Gate; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.
