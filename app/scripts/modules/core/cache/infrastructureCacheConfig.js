'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.cache.infrastructure.config', [])
  .constant('infrastructureCacheConfig', {
    networks: {
      version: 2,
    },
    vpcs: {
      version: 2,
    },
    subnets: {
      version: 2,
    },
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
      maxAge: 7 * 24 * 60 * 60 * 1000,
      version: 2
    },
  });
