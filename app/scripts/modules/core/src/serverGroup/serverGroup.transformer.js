'use strict';

const angular = require('angular');

import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';
import { ACCOUNT_SERVICE } from 'core/account/account.service';

module.exports = angular.module('spinnaker.core.serverGroup.transformer', [
  PROVIDER_SERVICE_DELEGATE,
  ACCOUNT_SERVICE
])
  .factory('serverGroupTransformer', function (providerServiceDelegate, accountService, $q) {
    function normalizeServerGroup(serverGroup, application) {
      const account = serverGroup.account;
      if (account) {
        return accountService.getAccountDetails(account)
          .then((accountDetails) => normalizeServerGroupForProviderVersion(serverGroup, application, accountDetails.providerVersion));
      } else {
        return $q.resolve(normalizeServerGroupForProviderVersion(serverGroup, application));
      }
    }

    function normalizeServerGroupForProviderVersion(serverGroup, application, providerVersion) {
      return providerServiceDelegate
        .getDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer', providerVersion)
        .normalizeServerGroup(serverGroup, application);
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
