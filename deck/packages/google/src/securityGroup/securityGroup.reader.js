'use strict';

import { module } from 'angular';

export const GOOGLE_SECURITYGROUP_SECURITYGROUP_READER = 'spinnaker.gce.securityGroup.reader';
export const name = GOOGLE_SECURITYGROUP_SECURITYGROUP_READER; // for backwards compatibility
module(GOOGLE_SECURITYGROUP_SECURITYGROUP_READER, []).factory('gceSecurityGroupReader', function () {
  function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.account].global[securityGroupId];
  }

  return {
    resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
  };
});
