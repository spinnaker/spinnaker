'use strict';

const angular = require('angular');

import { AccountService, INSTANCE_TYPE_SERVICE, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.cf.cache.initializer', [LOAD_BALANCER_READ_SERVICE, INSTANCE_TYPE_SERVICE])
  .factory('cfCacheConfigurer', function($q, instanceTypeService, loadBalancerReader) {
    let config = Object.create(null);

    config.credentials = {
      initializers: [() => AccountService.getCredentialsKeyedByAccount('cf')],
    };

    config.instanceTypes = {
      initializers: [() => instanceTypeService.getAllTypesByRegion('cf')],
    };

    config.loadBalancers = {
      initializers: [() => loadBalancerReader.listLoadBalancers('cf')],
    };

    return config;
  });
