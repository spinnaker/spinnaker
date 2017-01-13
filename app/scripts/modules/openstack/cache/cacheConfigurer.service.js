'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {SUBNET_READ_SERVICE} from 'core/subnet/subnet.read.service';

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
