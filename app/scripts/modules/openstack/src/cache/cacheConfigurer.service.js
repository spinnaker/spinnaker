'use strict';

const angular = require('angular');

import {
  ACCOUNT_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.openstack.cache.initializer', [
  ACCOUNT_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE,
])
  .factory('openstackCacheConfigurer', function (accountService, loadBalancerReader, networkReader, subnetReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.listAccounts('openstack') ],
    };

    config.account = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount('openstack') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('openstack') ],
    };

    config.networks = {
      initializers: [ () => networkReader.listNetworksByProvider('openstack') ],
    };

    config.subnets = {
      initializers: [ () => subnetReader.listSubnetsByProvider('openstack') ],
    };

    return config;
  });
