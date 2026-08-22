'use strict';

export class GceSecurityGroupReader {
  resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.account].global[securityGroupId];
  }
}
