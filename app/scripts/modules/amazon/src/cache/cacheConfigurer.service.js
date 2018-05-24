'use strict';

const angular = require('angular');

import { SubnetReader } from '@spinnaker/core';
import { VpcReader } from '../vpc/VpcReader';

module.exports = angular.module('spinnaker.amazon.cache.initializer', []).factory('awsCacheConfigurer', function() {
  let config = Object.create(null);

  // cache no longer used; version incremented and retained to clear any existing caches
  // remove this cache entry any time after June 2018
  config.instanceTypes = {
    version: 3,
  };

  // cache no longer used; version incremented and retained to clear any existing caches
  // remove this cache entry any time after June 2018
  config.loadBalancers = {
    version: 2,
  };

  config.subnets = {
    version: 2,
    initializers: [() => SubnetReader.listSubnets()],
  };

  config.networks = {
    version: 2,
    initializers: [() => VpcReader.listVpcs()],
    onReset: [() => VpcReader.resetCache()],
  };

  return config;
});
