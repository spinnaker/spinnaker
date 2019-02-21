'use strict';

const angular = require('angular');

import { AccountService, LOAD_BALANCER_READ_SERVICE, NetworkReader, SubnetReader } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.cache.initializer', [LOAD_BALANCER_READ_SERVICE])
  .factory('openstackCacheConfigurer', ['loadBalancerReader', function(loadBalancerReader) {
    let config = Object.create(null);

    config.credentials = {
      initializers: [() => AccountService.listAccounts('openstack')],
    };

    config.account = {
      initializers: [() => AccountService.getCredentialsKeyedByAccount('openstack')],
    };

    config.loadBalancers = {
      initializers: [() => loadBalancerReader.listLoadBalancers('openstack')],
    };

    config.networks = {
      initializers: [() => NetworkReader.listNetworksByProvider('openstack')],
    };

    config.subnets = {
      initializers: [() => SubnetReader.listSubnetsByProvider('openstack')],
    };

    return config;
  }]);
