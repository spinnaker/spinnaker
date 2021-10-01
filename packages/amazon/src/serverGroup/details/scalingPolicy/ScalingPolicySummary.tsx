import * as React from 'react';

import type { Application, IServerGroup } from '@spinnaker/core';

import { StepPolicySummary } from './StepPolicySummary';
import type { IScalingPolicyView, ITargetTrackingPolicy } from '../../../domain';
import { TargetTrackingSummary } from './targetTracking/TargetTrackingSummary';

export interface IScalingPolicySummaryProps {
  application: Application;
  policy: IScalingPolicyView;
  serverGroup: IServerGroup;
}

export const ScalingPolicySummary = ({ application, policy, serverGroup }: IScalingPolicySummaryProps) => {
  const scalingPolicyType = policy.policyType
    ? policy.policyType
    : policy.targetTrackingConfiguration
    ? 'TargetTrackingScaling'
    : 'StepScaling';

  if (scalingPolicyType === 'TargetTrackingScaling') {
    return (
      <TargetTrackingSummary
        application={application}
        policy={policy as ITargetTrackingPolicy}
        serverGroup={serverGroup}
      />
    );
  }

  return <StepPolicySummary application={application} policy={policy} serverGroup={serverGroup} />;
};
