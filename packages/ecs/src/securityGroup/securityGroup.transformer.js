'use strict';

import { module } from 'angular';

export const ECS_SECURITYGROUP_SECURITYGROUP_TRANSFORMER = 'spinnaker.ecs.securityGroup.transformer';
export const name = ECS_SECURITYGROUP_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
module(ECS_SECURITYGROUP_SECURITYGROUP_TRANSFORMER, []).factory('ecsSecurityGroupTransformer', function () {
  function normalizeSecurityGroup() {}

  return {
    normalizeSecurityGroup: normalizeSecurityGroup,
  };
});
