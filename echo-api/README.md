# echo-api

A module for defining plugin extension interfaces.

WARNING: This is an Alpha module and is not stable.  Consider this an experiment.

Currently there is one extension point `RestEventParser` which is limited to parsing events 
in echo's `RestEventListener`.  This is primarily for early iteration and testing purposes.

The more powerful extension point for Echo is likely to be `EchoEventListener` - this would enable a plugin
ecosystem for Echo and also allow us to break existing implementations of `EchoEventListener` 
(echo-rest, echo-notifications, echo-telemetry, etc) up into discrete plugins.
