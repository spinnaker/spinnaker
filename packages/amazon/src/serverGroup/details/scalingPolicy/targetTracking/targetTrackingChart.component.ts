import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { TargetTrackingChart } from './TargetTrackingChart';

export const TARGET_TRACKING_CHART_COMPONENT = 'spinnaker.amazon.scalingPolicy.targetTracking.chart.component';
module(TARGET_TRACKING_CHART_COMPONENT, []).component(
  'targetTrackingChart',
  react2angular(withErrorBoundary(TargetTrackingChart, 'targetTrackingChart'), [
    'alarmUpdated',
    'config',
    'serverGroup',
    'unit',
    'updateUnit',
  ]),
);
