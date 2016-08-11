# Caching Clouddriver

This set of clouddriver instances exists to periodically scan the configured
cloud providers infrastructure, and store it in the cache. It is not reachable
by any other services (explaining the empty `/svcs` directory), but the results 
it stores in the cache are read by gate from the read-only clouddriver.
