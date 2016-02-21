'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.cache.initializer', [
  require('../../core/account/account.service.js'),
  require('../../core/instance/instanceTypeService.js'),
  require('../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../core/network/network.read.service.js'),
  require('../../core/securityGroup/securityGroup.read.service.js'),
  require('../../core/subnet/subnet.read.service.js'),
])
  .factory('gceCacheConfigurer', function (accountService, instanceTypeService, loadBalancerReader,
                                           networkReader, subnetReader) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getRegionsKeyedByAccount('gce') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('gce') ],
    };

    config.loadBalancers = {
      initializers: [ () => loadBalancerReader.listLoadBalancers('gce') ],
    };

    config.networks = {
      initializers: [ () => networkReader.listNetworksByProvider('gce') ],
    };

    config.subnets = {
      initializers: [ () => subnetReader.listSubnetsByProvider('gce') ],
    };

    return config;
  });
