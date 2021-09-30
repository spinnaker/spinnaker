import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { StepPolicySummary } from './StepPolicySummary';

export const STEP_POLICY_SUMMARY_COMPONENT = 'spinnaker.amazon.scalingPolicy.stepPolicySummary.component';
module(STEP_POLICY_SUMMARY_COMPONENT, []).component(
  'stepPolicySummary',
  react2angular(withErrorBoundary(StepPolicySummary, 'stepPolicySummary'), ['application', 'policy', 'serverGroup']),
);
