'use strict';

export class OracleSecurityGroupReader {
  resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId) {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}
