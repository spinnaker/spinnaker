import { ISecurityGroup, ISecurityGroupsByAccount } from '@spinnaker/core';

export class KubernetesSecurityGroupReader {
  public resolveIndexedSecurityGroup(
    indexedSecurityGroups: ISecurityGroupsByAccount,
    container: ISecurityGroup,
    securityGroupId: string,
  ): ISecurityGroup {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}
