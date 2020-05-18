import { IInstance } from '@spinnaker/core';

export interface ITencentCloudInstance extends IInstance {
  targetGroups?: string[];
}
