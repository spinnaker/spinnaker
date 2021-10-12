import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { ScalingPolicySummary } from './ScalingPolicySummary';

export const SCALING_POLICY_SUMMARY_COMPONENT = 'spinnaker.amazon.scalingPolicy.scalingPolicySummary.component';
module(SCALING_POLICY_SUMMARY_COMPONENT, []).component(
  'scalingPolicySummary',
  react2angular(withErrorBoundary(ScalingPolicySummary, 'scalingPolicySummary'), [
    'application',
    'policy',
    'serverGroup',
  ]),
);
