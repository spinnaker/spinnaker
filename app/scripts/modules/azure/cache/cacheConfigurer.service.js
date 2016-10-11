'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.cache.initializer', [
  require('core/account/account.service.js'),
  require('core/loadBalancer/loadBalancer.read.service.js'),
  require('core/instance/instanceTypeService.js'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('../subnet/subnet.read.service.js'),
])
  .factory('azureCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, keyPairsReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getCredentialsKeyedByAccount('azure') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('azure') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('azure') ],
    };

    config.subnets = {
      version: 2,
      initializers: [subnetReader.listSubnets],
    };

    config.keyPairs = {
      initializers: [keyPairsReader.listKeyPairs]
    };

    return config;
  });
