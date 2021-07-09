'use strict';

import { module } from 'angular';

export const AZURE_SECURITYGROUP_SECURITYGROUP_READER = 'spinnaker.azure.securityGroup.reader';
export const name = AZURE_SECURITYGROUP_SECURITYGROUP_READER; // for backwards compatibility
module(AZURE_SECURITYGROUP_SECURITYGROUP_READER, []).factory('azureSecurityGroupReader', function () {
  function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    //hack to get around securityGroupId not matching id in indexedSecurityGroups.
    const temp = securityGroupId.split('/');
    return indexedSecurityGroups[container.account][container.region][temp[temp.length - 1]];
  }

  return {
    resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
  };
});
