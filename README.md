Spinnaker Auth Service
----------------------

[![Build Status](https://api.travis-ci.org/spinnaker/fiat.svg?branch=master)](https://travis-ci.org/spinnaker/fiat)

Fix it again, Travis

Roadmap/Implementation Punch list:
---
* ☑ **Redis-backed** - so that we can scale out to multiple Fiat instances.
* ☑ **Service accounts** - configuration file based definition of "users" that can be specified to "run as" during automated/triggered pipelines.
* ☑ **Account config from Clouddriver** - Instead of duplicating account config, get it from the source of truth.
* ☑ **Application config persistence** - ~~Accounts can be set in config files~~ because they're mostly static, but applications should be able to be dynamically created/destroyed, as well as their role settings persisted across reboots, rollouts, and  replicas. 
  * New Plan: Read application configs from Front50
* ☐ Compile "to secure" list of endpoints. (In progress)
* ☐ **Component integration** - wire it all together throughout the other microservices.
  * ☑ Gate
  * ☐ Front50 (in progress)
    * ☐ GET /default/applications
    * ☐ PUT /default/applications
    * ☐ POST /default/applications/batchUpdate
    * ☐ DELETE /default/applications/name/{application}
    * ☐ GET /default/applications/name/{application}
    * ☐ POST /default/applications/name/{application}
    * ☐ GET /default/applications/search
    * ☐ GET /default/applications/{application}/history
  * ☐ Orca
  * ☐ Clouddriver
  * ☐ Echo
* ☐ **Deck**
  * ☐ Add ability to specify group membership from application creation dialog
  * ☐ Generate warning if no application config is found, warning users that their application may be exposed.
* ☐ **Scaffolding** - Installation scripts, startup scripts, config files, etc.
