'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.transformer', [
  require('../cloudProvider/serviceDelegate.service.js'),
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

    // strips out Angular bits (see angular.js#toJsonReplacer), as well as executions and running tasks
    function jsonReplacer(key, value) {
      var val = value;

      if (typeof key === 'string' && key.charAt(0) === '$' && key.charAt(1) === '$') {
        val = undefined;
      }

      if (key === 'executions' || key === 'runningTasks') {
        val = undefined;
      }

      return val;
    }

    return {
      normalizeServerGroup: normalizeServerGroup,
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration,
      jsonReplacer: jsonReplacer,
    };

  });
