'use strict';

const angular = require('angular');

import { ACCOUNT_SERVICE, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.kubernetes.cache.initializer', [
  ACCOUNT_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
])
  .factory('kubernetesCacheConfigurer', function (accountService, loadBalancerReader) {

    let config = Object.create(null);

    config.account = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount('kubernetes') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('kubernetes') ],
    };

    return config;
  });
