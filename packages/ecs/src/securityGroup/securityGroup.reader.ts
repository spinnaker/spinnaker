import { module } from 'angular';

import { ISecurityGroup, ISecurityGroupsByAccount } from '@spinnaker/core';

export class EcsSecurityGroupReader {
  public resolveIndexedSecurityGroup(
    indexedSecurityGroups: ISecurityGroupsByAccount,
    container: ISecurityGroup,
    securityGroupId: string,
  ): ISecurityGroup {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}

export const ECS_SECURITY_GROUP_READER = 'spinnaker.ecs.securityGroup.reader';
module(ECS_SECURITY_GROUP_READER, []).service('ecsSecurityGroupReader', EcsSecurityGroupReader);
