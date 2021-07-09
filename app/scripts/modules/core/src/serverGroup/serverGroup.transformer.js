'use strict';

import { module } from 'angular';

import { PROVIDER_SERVICE_DELEGATE } from '../cloudProvider/providerService.delegate';

export const CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER = 'spinnaker.core.serverGroup.transformer';
export const name = CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER; // for backwards compatibility
module(CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('serverGroupTransformer', [
  'providerServiceDelegate',
  '$q',
  function (providerServiceDelegate, $q) {
    function normalizeServerGroup(serverGroup, application) {
      if (!providerServiceDelegate.hasDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer')) {
        return null;
      }
      return providerServiceDelegate
        .getDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer')
        .normalizeServerGroup(serverGroup, application);
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      const service = providerServiceDelegate.getDelegate(base.selectedProvider, 'serverGroup.transformer');
      return service ? service.convertServerGroupCommandToDeployConfiguration(base) : null;
    }

    return {
      normalizeServerGroup,
      convertServerGroupCommandToDeployConfiguration,
    };
  },
]);
