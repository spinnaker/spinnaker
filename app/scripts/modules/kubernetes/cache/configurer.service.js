'use strict';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';

let angular = require('angular');

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
