'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.gce.cache.initializer', [
  require('../../core/account/account.service.js'),
  require('../../core/instance/instanceTypeService.js'),
  require('../../core/securityGroup/securityGroup.read.service.js'),
])
  .factory('gceCacheConfigurer', function (accountService, instanceTypeService) {

    let config = Object.create(null);

    config.credentials = {
      initializers: [ () => accountService.getRegionsKeyedByAccount('gce') ],
    };

    config.instanceTypes = {
      initializers: [ () => instanceTypeService.getAllTypesByRegion('gce') ],
    };

    return config;
  })
  .name;
