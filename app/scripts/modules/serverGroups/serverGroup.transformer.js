'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.transformer', [
  require('../core/cloudProvider/serviceDelegate.service.js'),
])
  .factory('serverGroupTransformer', function (serviceDelegate) {

    function normalizeServerGroup(serverGroup) {
      return serviceDelegate.getDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer').
        normalizeServerGroup(serverGroup);
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      var service = serviceDelegate.getDelegate(base.selectedProvider, 'serverGroup.transformer');
      return service ? service.convertServerGroupCommandToDeployConfiguration(base) : null;
    }

    return {
      normalizeServerGroup: normalizeServerGroup,
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
    };

  })
  .name;
