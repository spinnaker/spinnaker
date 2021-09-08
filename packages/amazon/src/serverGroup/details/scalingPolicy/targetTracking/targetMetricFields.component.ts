import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';
import { TargetMetricFields } from './TargetMetricFields';

export const TARGET_TRACKING_TARGET_METRIC_FIELDS_COMPONENT =
  'spinnaker.amazon.scalingPolicy.targetTracking.metricFields.component';
module(TARGET_TRACKING_TARGET_METRIC_FIELDS_COMPONENT, []).component(
  'targetMetricFields',
  react2angular(withErrorBoundary(TargetMetricFields, 'targetMetricFields'), [
    'allowDualMode',
    'cloudwatch',
    'command',
    'isCustomMetric',
    'serverGroup',
    'toggleMetricType',
    'unit',
    'updateCommand',
  ]),
);
