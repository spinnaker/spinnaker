'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cf.cache.initializer', [
  require('../../account/account.service.js'),
  require('../../loadBalancers/loadBalancer.read.service.js'),
  require('../../instance/instanceTypeService.js'),
  require('../../securityGroups/securityGroup.read.service.js'),
])
  .factory('cfCacheConfigurer', function ($q,
                                         accountService, instanceTypeService, securityGroupReader,
                                         subnetReader, vpcReader, keyPairsReader, loadBalancerReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getRegionsKeyedByAccount('cf') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('cf') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('cf') ],
    };

    return config;
  })
  .name;
