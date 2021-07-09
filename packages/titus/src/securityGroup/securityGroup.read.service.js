'use strict';

import { module } from 'angular';

export const TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE = 'spinnaker.titus.securityGroup.reader';
export const name = TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE; // for backwards compatibility
module(TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE, []).factory('titusSecurityGroupReader', function () {
  function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.awsAccount][container.region][securityGroupId];
  }

  return {
    resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
  };
});
