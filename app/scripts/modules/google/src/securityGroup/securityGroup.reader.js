'use strict';

const angular = require('angular');

export const GOOGLE_SECURITYGROUP_SECURITYGROUP_READER = 'spinnaker.gce.securityGroup.reader';
export const name = GOOGLE_SECURITYGROUP_SECURITYGROUP_READER; // for backwards compatibility
angular.module(GOOGLE_SECURITYGROUP_SECURITYGROUP_READER, []).factory('gceSecurityGroupReader', function() {
  function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.account].global[securityGroupId];
  }

  return {
    resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
  };
});
