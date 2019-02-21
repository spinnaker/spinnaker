'use strict';

const angular = require('angular');

import { AccountService, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.kubernetes.cache.initializer', [LOAD_BALANCER_READ_SERVICE])
  .factory('kubernetesCacheConfigurer', [
    'loadBalancerReader',
    function(loadBalancerReader) {
      let config = Object.create(null);

      config.account = {
        initializers: [() => AccountService.getCredentialsKeyedByAccount('kubernetes')],
      };

      config.loadBalancers = {
        initializers: [() => loadBalancerReader.listLoadBalancers('kubernetes')],
      };

      return config;
    },
  ]);
