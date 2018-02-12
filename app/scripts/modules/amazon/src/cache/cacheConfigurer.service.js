'use strict';

const angular = require('angular');

import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular.module('spinnaker.amazon.cache.initializer', [
  VPC_READ_SERVICE,
])
  .factory('awsCacheConfigurer', function ($q, subnetReader, vpcReader) {

    let config = Object.create(null);

    // cache no longer used; version incremented and retained to clear any existing caches
    // remove this cache entry any time after June 2018
    config.instanceTypes = {
      version: 3
    };

    // cache no longer used; version incremented and retained to clear any existing caches
    // remove this cache entry any time after June 2018
    config.loadBalancers = {
      version: 2,
    };

    config.subnets = {
      version: 2,
      initializers: [() => subnetReader.listSubnets() ],
    };

    config.networks = {
      version: 2,
      initializers: [() => vpcReader.listVpcs() ],
      onReset: [() => vpcReader.resetCache() ],
    };

    return config;
  });
