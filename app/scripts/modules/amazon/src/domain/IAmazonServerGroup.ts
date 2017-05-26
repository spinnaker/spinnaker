import { IServerGroup } from '@spinnaker/core';

export interface IAmazonServerGroup extends IServerGroup {
  targetGroups: string[];
}
