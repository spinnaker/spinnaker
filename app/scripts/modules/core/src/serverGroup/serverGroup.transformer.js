'use strict';

import { module } from 'angular';

import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';
import { AccountService } from 'core/account/AccountService';

export const CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER = 'spinnaker.core.serverGroup.transformer';
export const name = CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER; // for backwards compatibility
module(CORE_SERVERGROUP_SERVERGROUP_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('serverGroupTransformer', [
  'providerServiceDelegate',
  '$q',
  function(providerServiceDelegate, $q) {
    function normalizeServerGroup(serverGroup, application) {
      const account = serverGroup.account;
      if (account) {
        return AccountService.getAccountDetails(account).then(accountDetails => {
          // If there is a versioned cloud provider, and the user does not have permission to view the account itself, it will
          // fail to get the accountDetails and thus fail to get the appropriate skin.
          return normalizeServerGroupForSkin(serverGroup, application, accountDetails && accountDetails.skin);
        });
      } else {
        return $q.resolve(normalizeServerGroupForSkin(serverGroup, application));
      }
    }

    function normalizeServerGroupForSkin(serverGroup, application, skin) {
      if (
        !providerServiceDelegate.hasDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer', skin)
      ) {
        return null;
      }
      return providerServiceDelegate
        .getDelegate(serverGroup.provider || serverGroup.type, 'serverGroup.transformer', skin)
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
