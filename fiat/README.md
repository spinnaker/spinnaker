Spinnaker Auth Service
----------------------

[![Build Status](https://github.com/spinnaker/spinnaker/actions/workflows/fiat.yml/badge.svg)](https://github.com/spinnaker/spinnaker/actions/workflows/fiat.yml)

```
   ____ _         ____ __    ___               _            ______                  _
  / __/(_)__ __  /  _// /_  / _ | ___ _ ___ _ (_)___       /_  __/____ ___ _ _  __ (_)___
 / _/ / / \ \ / _/ / / __/ / __ |/ _ `// _ `// // _ \ _     / /  / __// _ `/| |/ // /(_-<
/_/  /_/ /_\_\ /___/ \__/ /_/ |_|\_, / \_,_//_//_//_/( )   /_/  /_/   \_,_/ |___//_//___/
                                /___/                |/
```

Fiat is the authorization server for the Spinnaker system.

It exposes a RESTful interface for querying the access permissions for a particular user. It currently supports three kinds of resources:
* Accounts
* Applications
* Service Accounts

---

### Accounts
Accounts are setup within Clouddriver and queried by Fiat for its configured `requiredGroupMembership` restrictions.

### Applications
Applications are the combination of config metadata pulled from Front50 and server group names (e.g., application-stack-details). Application permissions sit beside application configuration in S3/Google Cloud Storage.

### Service Accounts
Fiat Service Accounts are groups that act as a user during automated triggers (say, from a GitHub push or Jenkins build). Authorization is built in by making the service account a member of a group specified in `requiredGroupMembership`.

---

### User Role/Authorization Providers
Currently supported user role providers are:
* Google Groups (through a Google Apps for Work organization)
* GitHub Teams
* LDAP
* File based role provider
* SAML Groups

---

### Modular builds
By default, Fiat is built with all authorization providers included. To build only a subset of
providers, use the `includeProviders` flag (run from the monorepo root):
 ```
./gradlew :fiat:clean :fiat:build -PincludeProviders=google-groups,ldap
```
 You can view the list of all providers in `gradle.properties`.

### Authorization client libraries

Authorization clients and shared permission models are published by Kork:

| Library | Artifact | Compatibility artifact |
| --- | --- | --- |
| Spring authorization integration and Fiat client | `io.spinnaker.kork:kork-authz` | `io.spinnaker.fiat:fiat-api` |
| Permission and resource models | `io.spinnaker.kork:kork-authz-api` | `io.spinnaker.fiat:fiat-core` |

Both Kork artifacts are managed by `kork-bom` and have no Fiat artifact dependencies.
The compatibility artifacts re-export the Kork libraries and contain no duplicate classes.
Java packages remain under `com.netflix.spinnaker.fiat`, and `services.fiat` configuration
and authorization behavior are unchanged.

Run the authorization and server tests from the monorepo root:

```sh
./gradlew :kork:kork-authz:test :kork:kork-authz-api:test :fiat:test
```

Fiat's `fiat-api:test` also compiles and runs an external consumer with each dependency
pair, verifies that the compatibility jars contain no classes, and checks that the Kork
consumer's resolved dependencies contain no Fiat artifacts. The server tests require Docker.

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true` (run from the monorepo root):
```
./gradlew fiat -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7103.  The JVM will _not_ wait for the debugger
to be attached before starting Fiat; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.
