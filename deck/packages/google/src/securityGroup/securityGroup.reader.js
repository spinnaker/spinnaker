'use strict';

export const GOOGLE_SECURITYGROUP_SECURITYGROUP_READER = 'spinnaker.gce.securityGroup.reader';
export const name = GOOGLE_SECURITYGROUP_SECURITYGROUP_READER; // for backwards compatibility
export class GceSecurityGroupReader {
  resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.account].global[securityGroupId];
  }
}
