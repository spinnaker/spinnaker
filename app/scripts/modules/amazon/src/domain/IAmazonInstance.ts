import { IInstance } from '@spinnaker/core';

export interface IAmazonInstance extends IInstance {
  targetGroups?: string[];
}
