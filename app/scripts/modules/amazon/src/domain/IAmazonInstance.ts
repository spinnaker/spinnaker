import { IInstance } from '@spinnaker/core';
import { IAmazonSecurityGroup } from './IAmazonSecurityGroup';

export interface IAmazonInstance extends IInstance {
  imageId?: string;
  instanceType?: string;
  capacityType?: string;
  securityGroups?: IAmazonSecurityGroup[];
  subnetId?: string;
  targetGroups?: string[];
}
