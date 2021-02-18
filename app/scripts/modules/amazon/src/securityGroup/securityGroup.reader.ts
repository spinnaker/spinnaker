import { module } from 'angular';

import { ISecurityGroup, ISecurityGroupsByAccount } from '@spinnaker/core';

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

export const AWS_SECURITY_GROUP_READER = 'spinnaker.amazon.securityGroup.reader';
module(AWS_SECURITY_GROUP_READER, []).service('awsSecurityGroupReader', AwsSecurityGroupReader);
