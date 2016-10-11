'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.cache.initializer', [
  require('core/account/account.service.js'),
  require('core/loadBalancer/loadBalancer.read.service.js'),
  require('core/instance/instanceTypeService.js'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/network/network.read.service.js'),
  require('core/subnet/subnet.read.service.js'),
])
  .factory('openstackCacheConfigurer', function (accountService, instanceTypeService, loadBalancerReader, networkReader, subnetReader) {

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
