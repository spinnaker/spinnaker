import { IInstance } from '@spinnaker/core';

export interface ITencentcloudInstance extends IInstance {
  targetGroups?: string[];
}
