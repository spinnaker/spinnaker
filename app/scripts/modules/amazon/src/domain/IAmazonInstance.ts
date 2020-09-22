import { IInstance } from '@spinnaker/core';

export interface IAmazonInstance extends IInstance {
  imageId?: string;
  instanceType?: string;
  subnetId?: string;
  targetGroups?: string[];
}
