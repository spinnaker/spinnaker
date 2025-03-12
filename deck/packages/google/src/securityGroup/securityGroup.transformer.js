'use strict';

import { module } from 'angular';

export const GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER = 'spinnaker.gce.securityGroup.transformer';
export const name = GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
module(GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER, []).factory('gceSecurityGroupTransformer', [
  '$q',
  function ($q) {
    function normalizeSecurityGroup(securityGroup) {
      return $q.when(securityGroup); // no-op
    }

    return {
      normalizeSecurityGroup: normalizeSecurityGroup,
    };
  },
]);
