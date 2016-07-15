Spinnaker Auth Service
----------------------

[![Build Status](https://api.travis-ci.org/spinnaker/fiat.svg?branch=master)](https://travis-ci.org/spinnaker/fiat)

Fix it again, Travis

Roadmap/Implementation Punch list:
---
* ☑ **Redis-backed** - so that we can scale out to multiple Fiat instances.
* ☑ **Service accounts** - configuration file based definition of "users" that can be specified to "run as" during automated/triggered pipelines.
* ☐ **Account config from Clouddriver** - Instead of duplicating account config, get it from the source of truth.
* ☐ **Application config persistence** - ~~Accounts can be set in config files~~ because they're mostly static, but applications should be able to be dynamically created/destroyed, as well as their role settings persisted across reboots, rollouts, and  replicas. 
  * New Plan: Read application configs from Front50
* ☐ **Component integration** - wire it all together throughout the other microservices.
  * Gate
  * Orca
  * Clouddriver
  * Front50
  * Echo

