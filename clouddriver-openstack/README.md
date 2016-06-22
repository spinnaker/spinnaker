# Openstack Clouddriver

## Caching flow
The purpose of this section is to detail how caching is implemented in a high-level flow

Cache Loading
1. OpenstackInfrastructureProviderConfig gets loaded and builds a new OpenstackInfrastructureProvider
2. During the creation in step #1, it will add new caching agents for each account into the OpenstackInfrastructureProvider
3. OpenstackInfrastructureProvider is picked up by CATS within configuration (See cache config) and agents are scheduled
4. Agents implement the loadData method and return an implementation of CacheResult which will be stored in redis

Cache Retrieving
1. Entity level providers (i.e. InstanceProvider) are invoked by respective controllers and using a Cache abstraction
can query REDIS for entity data. All data is namespaced by provider, type, and region.
2. Data retrieved from the cache is mapped into model object that implements a contract interface.


