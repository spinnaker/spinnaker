'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.cache.initializer', [
  require('../../core/account/account.service.js'),
  require('../../loadBalancers/loadBalancer.read.service.js'),
  require('../../instance/instanceTypeService.js'),
  require('../../securityGroups/securityGroup.read.service.js'),
  require('../subnet/subnet.read.service.js'),
  require('../vpc/vpc.read.service.js'),
  require('../keyPairs/keyPairs.read.service.js'),
])
  .factory('awsCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getRegionsKeyedByAccount('aws') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('aws') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('aws') ],
    };

    config.subnets = {
      version: 2,
      initializers: [subnetReader.listSubnets],
    };

    config.vpcs = {
      version: 2,
      initializers: [vpcReader.listVpcs],
      onReset: [vpcReader.resetCache],
    };

    config.keyPairs = {
      initializers: [keyPairsReader.listKeyPairs]
    };

    return config;
  })
  .name;
