import type { Application, IServerGroup } from '@spinnaker/core';
import type { IScalingPolicy } from '../../../domain';

export interface IScalingPolicySummaryProps {
  policy: IScalingPolicy;
  serverGroup: IServerGroup;
  application: Application;
}
