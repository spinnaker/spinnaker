Cache All The Stuff
===================

Utilities for cache population

Usage
-----

A ``Provider`` is the owner of one or more ``CachingAgent``s.

A ``CatsModule`` configures the various components needed to schedule execution of each ``CachingAgent`` for
 each ``Provider``. ``new CatsModule.Builder().build(aProvider)`` comes configured with reasonable defaults for
 in-memory caching and refreshing on a fixed interval. The ``CatsModule.getView()`` returns a ``Cache`` that
 provides read access to the data populated by the ``CachingAgent``s

To gain insight into the execution of the ``CachingAgent``s, one or more ``ExecutionInstrumentation`` can be provided which
 will receive callbacks around the start, completion, or failure of each ``CachingAgent.loadData()``

CacheData
---------

``CacheData`` stored in the ``Cache`` is classified by a ``type`` and identified by an ``id`` (for example
 ``type: application, id: myapp``). ``CacheData`` has attributes - facts about the item such as an application owner's
 email address, as well as relationships - references to other items in the cache.

CachingAgent
------------

A ``CachingAgent`` belongs to a ``Provider`` and is identified by its ``CachingAgent.agentType``. The ``CacheResult``
 returned by the ``CachingAgent`` contains ``CacheData`` for all the ``CachingAgent.providedDataTypes``. A ``CachingAgent``
 can be either an ``AgentDataType.Authority.AUTHORITATIVE`` source of data, or an ``AgentDataType.Authority.INFORMATIVE``
 source of data.  An AUTHORITATIVE source of data means the agent knows that it has the complete set of data for a
 particular type. For example an agent that cached Amazon AutoScalingGroups by performing describeAutoScalingGroup would
 authoritatively know the set AutoScalingGroups because it sees them all, but only informatively know about LoadBalancers
 or Instances because it only sees those associated with an AutoScalingGroup.
