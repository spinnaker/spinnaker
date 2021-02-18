import { IScalingPolicy } from 'amazon/domain';

import { Application, IServerGroup } from '@spinnaker/core';

export interface IScalingPolicySummaryProps {
  policy: IScalingPolicy;
  serverGroup: IServerGroup;
  application: Application;
}
