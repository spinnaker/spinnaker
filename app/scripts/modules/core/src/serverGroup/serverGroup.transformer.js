'use strict';

const angular = require('angular');

import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

module.exports = angular.module('spinnaker.core.serverGroup.transformer', [
  PROVIDER_SERVICE_DELEGATE,
])
  .factory('serverGroupTransformer', function (providerServiceDelegate) {

    function normalizeServerGroup(serverGroup, application) {
      return providerServiceDelegate.getDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer').
        normalizeServerGroup(serverGroup, application);
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      var service = providerServiceDelegate.getDelegate(base.selectedProvider, 'serverGroup.transformer');
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
