### Canary Config Index
#### Goals
* Support efficient querying of canary configs scoped by application(s)
* Prevent naming collisions between canary configs scoped by application(s)
* Include canary configs that are the target of in-flight additions/updates in queried canary config lists
* Prevent canary configs that are the target of in-flight deletions from appearing in queried canary config lists
* Respond consistently to CRUD operations whether background re-indexing operations are underway or not
* Behave consistently no matter how many kayenta nodes are operating in unison, with no additional configuration

#### Key Points
* The index affects only the management of canary configs and has no impact on the other types of resources under management.
* Querying a canary config by id (that is, a `GET` of `/canaryConfig/{canaryConfigId}`) does not make use of the index.
* No lists, updates or deletions of canary configs can take place if the index is unavailable.
* Each mutating call results in pending update entries being written to redis. This pending update is 'opened' before the mutating call is made,
and 'closed' after the mutating call successfully completes. If the call fails, the pending update is removed.
* An indexing agent runs in the background and builds in redis a map from each configuration store account's applications to its scoped canary configs.
It does this by iterating over all of the canary configs persisted in that store/account combination.
* Canary config lists are composed by first consulting the index, and then applying all pending updates (that is, the mutations that
we know occurred but that may not have been encountered by the indexing agent yet).
* In addition to building the indexes themselves, the agent is responsible for flushing from the pending updates queue matching start/finish entries.
Stale unmatched entries will also be flushed.
* All canary configs must specify at least one application.
* For a canary config create/update request, the union of the specified applications (they are specified within the body of the canary config) is considered
when determining if there is a possible naming collision.
* For a canary config list request, the union of the specified applications' canary configs is returned. If no applications are specified, all canary configs
are included in the response list.
