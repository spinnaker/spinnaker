import { module } from 'angular';
import { react2angular } from 'react2angular';
import { withErrorBoundary } from '@spinnaker/core';
import { TitusTargetTrackingChart } from './TitusTargetTrackingChart';

export const TARGET_TRACKING_CHART_COMPONENT = 'spinnaker.titus.scalingPolicy.targetTracking.chart.component';
module(TARGET_TRACKING_CHART_COMPONENT, []).component(
  'titusTargetTrackingChart',
  react2angular(withErrorBoundary(TitusTargetTrackingChart, 'targetTrackingChart'), [
    'alarmUpdated',
    'config',
    'serverGroup',
    'unit',
    'updateUnit',
  ]),
);
