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
  * ☑ Front50 (in progress)
    * Troublesome:
      * DELETE /pipelines/deleteById
      * DELETE /strategies/deleteById
      * NotificationsController
  * ☐ Orca
  * ☐ Clouddriver
  * ☐ Echo
* ☐ **Deck**
  * ☐ Add ability to specify group membership from application creation dialog
  * ☐ Generate warning if no application config is found, warning users that their application may be exposed.
* ☐ **Scaffolding** - Installation scripts, startup scripts, config files, etc.
