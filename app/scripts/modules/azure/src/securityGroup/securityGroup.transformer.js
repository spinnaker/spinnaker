'use strict';

const angular = require('angular');

export const AZURE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER = 'spinnaker.azure.securityGroup.transformer';
export const name = AZURE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
angular.module(AZURE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER, []).factory('azureSecurityGroupTransformer', function() {
  function normalizeSecurityGroup() {}

  return {
    normalizeSecurityGroup: normalizeSecurityGroup,
  };
});
