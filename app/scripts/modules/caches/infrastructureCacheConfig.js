'use strict';

angular.module('deckApp.caches.infrastructure.config', [])
  .constant('infrastructureCacheConfig', {
    credentials: {},
    vpcs: {},
    subnets: {},
    applications: {
      maxAge: 30 * 24 * 60 * 60 * 1000 // 30 days - it gets refreshed every time the user goes to the application list, anyway
    },
    loadBalancers: {
      maxAge: 60 * 60 * 1000
    },
    securityGroups: {
      version: 2 // increment to force refresh of cache on next page load - can be added to any cache
    },
    instanceTypes: {
      maxAge: 7 * 24 * 60 * 60 * 1000
    },
    keyPairs: {},
    buildMasters: {
      maxAge: 7 * 24 * 60 * 60 * 1000
    },
    buildJobs: {
      maxAge: 7 * 24 * 60 * 60 * 1000
    }
  });
