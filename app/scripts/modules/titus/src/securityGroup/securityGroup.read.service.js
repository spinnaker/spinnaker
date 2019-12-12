'use strict';

import { module } from 'angular';

export const TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE = 'spinnaker.titus.securityGroup.reader';
export const name = TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE; // for backwards compatibility
module(TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE, []).factory('titusSecurityGroupReader', function() {
  function resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    // TODO: this is bad, but this method is not async and making it async is going to be non-trivial
    const account = container.account
      .replace('titus', '')
      .replace('vpc', '')
      .replace('dev', 'test')
      .replace('prodmce', 'mceprod')
      .replace('mcestaging', 'mcetest')
      .replace('testmce', 'mcetest');
    return indexedSecurityGroups[account][container.region][securityGroupId];
  }

  return {
    resolveIndexedSecurityGroup: resolveIndexedSecurityGroup,
  };
});
