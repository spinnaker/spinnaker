import { ISecurityGroupsByAccount, ISecurityGroup } from '@spinnaker/core';

export class KubernetesSecurityGroupReader {
  public static resolveIndexedSecurityGroup(
    indexedSecurityGroups: ISecurityGroupsByAccount,
    container: ISecurityGroup,
    securityGroupId: string,
  ): ISecurityGroup {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}
