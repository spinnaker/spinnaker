import type { ISecurityGroup, ISecurityGroupsByAccount } from '@spinnaker/core';

export class AwsSecurityGroupReader {
  public resolveIndexedSecurityGroup(
    indexedSecurityGroups: ISecurityGroupsByAccount,
    container: ISecurityGroup,
    securityGroupId: string,
  ): ISecurityGroup {
    return AwsSecurityGroupReader.resolveIndexedSecurityGroup(indexedSecurityGroups, container, securityGroupId);
  }

  public static resolveIndexedSecurityGroup(
    indexedSecurityGroups: ISecurityGroupsByAccount,
    container: ISecurityGroup,
    securityGroupId: string,
  ): ISecurityGroup {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}
