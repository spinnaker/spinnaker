Echo
----

Echo is an event propagation micro-service that is intended as a central source of event and history information within
Spinnaker.

Event Propagation in Echo
=========================

Echo will send events to beans within the application context that implement the EchoEventListener interface.

This means that listeners besides the existing Cassandra and Elastic Search implementations can be added by including new libraries.

Search
======

In this implementation, event search is provided by an elastic search instance.

endpoints tbd
