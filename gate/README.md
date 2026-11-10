Spinnaker Gateway Service
------------------------------------
[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/gate.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/gate.yml)

This service provides the Spinnaker REST API, servicing scripting clients as well as all actions from [Deck](../deck).
The REST API fronts the following services:
* [CloudDriver](../clouddriver)
* [Front50](../front50)
* [Igor](../igor)
* [Orca](../orca)

### Modular builds
By default, Gate is built with all authentication providers included. To build only a subset of
providers, use the `includeProviders` flag (run from the monorepo root):
 ```
./gradlew :gate:clean :gate:build -PincludeProviders=oauth2,x509
```
 You can view the list of all providers in `gradle.properties`.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):

```
./gradlew gate -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 8184.  The JVM will _not_ wait for
the debugger to be attached before starting Gate; the relevant JVM arguments can be seen and
modified as needed in `build.gradle`.

### Accepted request locales

Tomcat limits the number of locales parsed from the `Accept-Language` request headers
(default: 10 in Tomcat 11.0.27). Requests exceeding that limit can fail when Spring
resolves the request locale, including ordinary Deck API requests.

To override the limit, set `default.maxLocaleCount` in a Gate Spring profile such as
`gate-local.yml`:

```yaml
default:
  maxLocaleCount: 25
```

This applies to the main connector and any configured legacy HTTP or x509 API connectors.
Leaving the property unset preserves Tomcat's default. Any negative value, such as `-1`,
disables the locale-count limit, following
[Tomcat's connector semantics](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html).
Other HTTP header limits still apply.
