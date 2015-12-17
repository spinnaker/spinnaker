'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.cache.initializer', [
  require('../../core/account/account.service.js'),
  require('../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../core/instance/instanceTypeService.js'),
  require('../../core/securityGroup/securityGroup.read.service.js'),
  require('../subnet/subnet.read.service.js'),
  require('../vpc/vpc.read.service.js'),
])
  .factory('awsCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.listAccounts('aws') ],
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

    config.networks = {
      version: 2,
      initializers: [vpcReader.listVpcs],
      onReset: [vpcReader.resetCache],
    };

    return config;
  });
