# kork-runtime

This module serves as a collection of the runtime dependencies in the kork
project that should be added to the service module for a standard Spinnaker
service - similar to a spring-boot-starter style module for Spinnaker
services.

As we add new or refactor existing runtime only dependencies we can collect
the dependencies here so that as we autobump out kork releases services will
pick up those changes.

