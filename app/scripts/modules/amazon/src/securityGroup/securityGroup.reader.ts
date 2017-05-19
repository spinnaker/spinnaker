import { module } from 'angular';

import { IIndexedSecurityGroups, ISecurityGroup } from '@spinnaker/core';

export class AwsSecurityGroupReader {

  public resolveIndexedSecurityGroup(indexedSecurityGroups: IIndexedSecurityGroups, container: ISecurityGroup, securityGroupId: string): ISecurityGroup  {
    return indexedSecurityGroups[container.account][container.region][securityGroupId];
  }
}

export const AWS_SECURITY_GROUP_READER = 'spinnaker.amazon.securityGroup.reader';
module(AWS_SECURITY_GROUP_READER, []).service('awsSecurityGroupReader', AwsSecurityGroupReader);
