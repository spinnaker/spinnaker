import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { TargetTrackingSummary } from './TargetTrackingSummary';

export const TARGET_TRACKING_SUMMARY_COMPONENT = 'spinnaker.amazon.scalingPolicy.targetTracking.summary.component';
module(TARGET_TRACKING_SUMMARY_COMPONENT, []).component(
  'targetTrackingSummary',
  react2angular(withErrorBoundary(TargetTrackingSummary, 'stepPolicySummary'), [
    'application',
    'policy',
    'serverGroup',
  ]),
);
