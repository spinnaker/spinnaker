'use strict';

const angular = require('angular');

import { AccountService, LOAD_BALANCER_READ_SERVICE, NETWORK_READ_SERVICE, SUBNET_READ_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.cache.initializer', [
    LOAD_BALANCER_READ_SERVICE,
    NETWORK_READ_SERVICE,
    SUBNET_READ_SERVICE,
  ])
  .factory('openstackCacheConfigurer', function(loadBalancerReader, networkReader, subnetReader) {
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
      initializers: [() => networkReader.listNetworksByProvider('openstack')],
    };

    config.subnets = {
      initializers: [() => subnetReader.listSubnetsByProvider('openstack')],
    };

    return config;
  });
