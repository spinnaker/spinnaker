'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.cache.initializer', [
  require('../../core/account/account.service.js'),
  require('../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../core/instance/instanceTypeService.js'),
  require('../../core/securityGroup/securityGroup.read.service.js'),
  require('../subnet/subnet.read.service.js'),
  require('../vpc/vpc.read.service.js'),
  require('../keyPairs/keyPairs.read.service.js'),
])
  .factory('azureCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getRegionsKeyedByAccount('azure') ],
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

    config.vpcs = {
      version: 2,
      initializers: [vpcReader.listVpcs],
    };

    config.keyPairs = {
      initializers: [keyPairsReader.listKeyPairs]
    };

    return config;
  });
