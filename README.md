Spinnaker Auth Service
----------------------

[![Build Status](https://api.travis-ci.org/spinnaker/fiat.svg?branch=master)](https://travis-ci.org/spinnaker/fiat)

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
There are currently two user role providers: Google Groups (through a Google Apps for Work organization) and GitHub Teams. If you would like to see additional providers, see [this issue](https://github.com/spinnaker/spinnaker/issues/2437).

---

Roadmap/Implementation Punch list has been moved to [Milestone 1 Issues](https://github.com/spinnaker/fiat/milestone/1)

### Debugging

To start the JVM in debug mode, set the Java system property `DEBUG=true`:
```
./gradlew -DDEBUG=true
```

The JVM will then listen for a debugger to be attached on port 7103.  The JVM will _not_ wait for the debugger
to be attached before starting Fiat; the relevant JVM arguments can be seen and modified as needed in `build.gradle`.